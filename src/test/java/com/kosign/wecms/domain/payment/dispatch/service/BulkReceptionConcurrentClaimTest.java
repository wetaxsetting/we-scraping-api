package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-4 · <b>Real two-thread race</b> across the bulk-reception egress boundary.
 *
 * <p>This is the test that would have failed before the fix. It runs two genuine threads through
 * {@link BulkReceptionService#receive} for the <em>same</em> logical bulk file, released
 * simultaneously by a {@link CountDownLatch}, and asserts that the irreversible external calls —
 * SFTP upload and EFS 00301 — each happen <b>exactly once</b>.
 *
 * <h2>Why a hand-written fake repository rather than a Mockito mock</h2>
 * A Mockito stub returning a fixed value cannot express "the first caller wins and every later caller
 * loses" — it would answer the same thing to both threads and the test would pass regardless of
 * whether the production code was safe. The fake below implements
 * {@code claimForReception} with a real compare-and-set on an {@link AtomicReference} holding the
 * row's {@code PROC_STAT}, which is precisely the semantics of the conditional
 * {@code UPDATE … SET proc_stat='6' WHERE … AND proc_stat='9'}: at most one caller observes the
 * transition, everyone else is told 0 rows changed.
 *
 * <p><b>Honest limitation.</b> This proves the <em>service</em> contract — that a losing claim
 * results in zero external calls — under real thread interleaving. It does not execute PostgreSQL's
 * row locking, because the project ships no test database (no Testcontainers, no embedded DB; see
 * {@code CLAUDE.md}). The other half of the guarantee — that the SQL really is a single conditional
 * UPDATE and not a read-then-write — is pinned separately and statically by
 * {@code DispatchClaimQueryContractTest}. Together they cover both halves; neither alone would.
 * The CI command that would close the gap is recorded in
 * {@code docs/architecture/bulk-dispatch-concurrency-fix.md}.
 *
 * <p>No network is touched: the SFTP client and the EFS client are recording fakes.
 */
class BulkReceptionConcurrentClaimTest {

    private static final String TRAN_DT = "20260729";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String OTHER_BIZ_KEY = "VN_VNM0004_000014";
    private static final String FILE_SEQ_NO = "1";
    private static final String OUT_BANK = "970457";
    private static final String WDRL = "100001020691";
    private static final String VAN = "GV00000001";

    /**
     * Models one {@code TB_PAY_FILE_MST} row's {@code PROC_STAT} plus the atomic conditional update.
     * Keyed by the logical file identity so the cross-tenant case can be exercised too.
     */
    private static final class FakePayFileState {
        private final AtomicReference<String> procStat = new AtomicReference<>(ProcStat.WAIT.code());
        private final AtomicInteger claimAttempts = new AtomicInteger();

        /** The conditional UPDATE: succeeds only from WAIT, and only once. */
        int claim() {
            claimAttempts.incrementAndGet();
            return procStat.compareAndSet(ProcStat.WAIT.code(), ProcStat.REQ.code()) ? 1 : 0;
        }
    }

    /** Records every egress call so the test can count them; never touches a network. */
    private static final class RecordingSftpClient implements SftpClient {
        final List<String> uploads = new CopyOnWriteArrayList<>();

        @Override
        public void upload(String localPath, String remotePath) {
            uploads.add(remotePath);
        }

        @Override
        public void download(String remotePath, String localPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void move(String remoteFrom, String remoteTo) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * One state per tenant, keyed by {@code BIZ_KEY}. Both the row read and the claim consult this
     * map, so the cross-tenant case is modelled the way the real claim key behaves: {@code FILE_SEQ_NO}
     * is allocated from a global sequence and is therefore <b>not</b> unique on its own.
     */
    private final java.util.Map<String, FakePayFileState> states = new java.util.concurrent.ConcurrentHashMap<>();
    private FakePayFileState payState;
    private RecordingSftpClient sftp;
    private AtomicInteger reception301Calls;

    private FakePayFileState stateFor(String bizKey) {
        return states.computeIfAbsent(bizKey, k -> new FakePayFileState());
    }

    private TbPayFileMstRepository payFileMstRepository;
    private TbPayFileDataRepository payFileDataRepository;
    private TbFirmConfRepository firmConfRepository;
    private TbFirmFileMstRepository firmFileMstRepository;
    private TbFirmFileDataRepository firmFileDataRepository;
    private TbFirmBatApiTranRepository firmBatApiTranRepository;
    private BulkTransferValidator validator;
    private OperatingHoursService operatingHoursService;
    private DispatchKeyGenerator dispatchKeyGenerator;

    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        states.clear();
        payState = stateFor(BIZ_KEY);
        sftp = new RecordingSftpClient();
        reception301Calls = new AtomicInteger();

        payFileMstRepository = mock(TbPayFileMstRepository.class);
        payFileDataRepository = mock(TbPayFileDataRepository.class);
        firmConfRepository = mock(TbFirmConfRepository.class);
        firmFileMstRepository = mock(TbFirmFileMstRepository.class);
        firmFileDataRepository = mock(TbFirmFileDataRepository.class);
        firmBatApiTranRepository = mock(TbFirmBatApiTranRepository.class);
        validator = mock(BulkTransferValidator.class);
        operatingHoursService = mock(OperatingHoursService.class);
        dispatchKeyGenerator = mock(DispatchKeyGenerator.class);

        // The pay-file row: always readable, and its PROC_STAT reflects the shared fake state so the
        // top-of-method fast-path check sees whatever the race has produced so far.
        // The row read ALWAYS reports WAIT, deliberately.
        //
        // The check at the top of receive() is an advisory fast-path, not the guard. If this stub
        // mirrored the claimed state, a thread that happened to read after the winner had claimed
        // would short-circuit there and never reach the claim — which is correct production
        // behaviour, but it makes the race non-deterministic and means the test might exercise the
        // fast-path instead of the thing under test. Pinning the read to WAIT forces every driver
        // through to the claim, which is both the worst case and the only case that proves the
        // atomic guard works. (Observed for real: with a state-mirroring stub the 4-thread case
        // intermittently saw fewer than 4 claim attempts.)
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), anyString(), anyString()))
                .thenAnswer(i -> Optional.of(payRow(i.getArgument(1), ProcStat.WAIT.code())));
        // THE claim — a real CAS per tenant, not a canned answer.
        when(payFileMstRepository.claimForReception(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(i -> stateFor(i.getArgument(0)).claim());
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(anyString(), anyString(), anyString()))
                .thenReturn(List.of(detail()));
        when(payFileDataRepository.aggregateValidationCounts(anyString(), anyString(), anyString()))
                .thenReturn(mock(com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts.class));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(firmConf()));
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(validator.validate(any(), any())).thenReturn(Optional.empty());
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);

        SftpProperties sftpProperties = new SftpProperties();

        // Count 00301 issuance by overriding the API leg — no gateway, no crypto, no network.
        service = new BulkReceptionService(
                mock(EfsApiClient.class), new EfsCryptoService(), mock(VanApiKeyService.class),
                mock(EfsProperties.class), new ObjectMapper(),
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, new FixedLengthCodec(), sftp, sftpProperties, validator,
                mock(com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService.class),
                operatingHoursService,
                new BulkDispatchClaimTxService(payFileMstRepository, firmFileMstRepository),
                mock(com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.class),
                mock(com.kosign.wecms.domain.payment.common.OutBankCdNormalizer.class),
                new NoOpTransactionManager()) {
            @Override
            public BulkReceptionApiResult issueReception(BulkReceptionApiCommand cmd) {
                reception301Calls.incrementAndGet();
                return new BulkReceptionApiResult(cmd.bankCd(), cmd.prtnId(), cmd.fileNm(),
                        cmd.dataCnt(), cmd.dataAmt(), cmd.dataDcmAmt());
            }
        };
    }

    // ── the race ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("two concurrent drivers: exactly one SFTP upload and exactly one EFS 00301")
    void twoConcurrentDrivers_produceExactlyOneEgress() throws Exception {
        runRace(2, () -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO));

        assertThat(payState.claimAttempts.get())
                .as("both threads must actually reach the claim — otherwise the race did not happen "
                        + "and this test proves nothing")
                .isEqualTo(2);
        assertThat(sftp.uploads)
                .as("SFTP upload must happen exactly once — a second upload is a duplicate "
                        + "bulk-payment file delivered to the bank")
                .hasSize(1);
        assertThat(reception301Calls.get())
                .as("EFS 00301 must be issued exactly once — a second issuance is a duplicate "
                        + "reception instruction for money already accepted")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("normal after-commit hand-off racing a recovery sweep still yields one egress")
    void normalFlowRacingRecovery_producesExactlyOneEgress() throws Exception {
        // Same entry point, which is the point: DispatchAutoReceiveRegistrar.afterCommit and
        // StrandedRecoveryService.recoverPayFiles both call receive(...) with the same key, so they
        // contend on the same claim. This is the concrete pairing that made H-4 reachable.
        runRace(2, () -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO));

        assertThat(sftp.uploads).hasSize(1);
        assertThat(reception301Calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("four concurrent drivers (two nodes x two paths): still exactly one egress")
    void fourConcurrentDrivers_produceExactlyOneEgress() throws Exception {
        runRace(4, () -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO));

        assertThat(payState.claimAttempts.get()).isEqualTo(4);
        assertThat(sftp.uploads).hasSize(1);
        assertThat(reception301Calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the loser performs no external call and no firm-file insert")
    void loser_performsNoEgressAndNoInsert() throws Exception {
        // Pre-claim the row so the single execution is guaranteed to be the loser. Because the row
        // read is pinned to WAIT, this execution passes the advisory fast-path and is stopped by the
        // CLAIM — which is precisely the path that matters.
        assertThat(payState.claim()).isEqualTo(1);

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        assertThat(sftp.uploads).as("loser must not upload").isEmpty();
        assertThat(reception301Calls.get()).as("loser must not issue 00301").isZero();
        org.mockito.Mockito.verify(firmFileMstRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(firmFileDataRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("a claim cannot be re-won once the file has left WAIT (sent / failed / completed)")
    void claimCannotOverwriteANonWaitState() {
        for (String terminal : new String[] {
                ProcStat.REQ.code(), ProcStat.SUCC.code(), ProcStat.FAIL.code()}) {
            FakePayFileState state = new FakePayFileState();
            state.procStat.set(terminal);
            assertThat(state.claim())
                    .as("PROC_STAT=%s must not be claimable — the file is already in flight, "
                            + "settled or failed", terminal)
                    .isZero();
        }
    }

    @Test
    @DisplayName("the same logical file number under a different tenant is an independent claim")
    void sameLogicalFile_differentTenant_isIndependent() throws Exception {
        // FILE_SEQ_NO is allocated from the GLOBAL firm_file_seq_no sequence, so two tenants can hold
        // the same value. Because BIZ_KEY is part of the claim predicate they must not contend: one
        // tenant claiming must never block the other from dispatching its own file.
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        service.receive(TRAN_DT, OTHER_BIZ_KEY, FILE_SEQ_NO);

        assertThat(stateFor(BIZ_KEY).claimAttempts.get()).isEqualTo(1);
        assertThat(stateFor(OTHER_BIZ_KEY).claimAttempts.get()).isEqualTo(1);
        assertThat(sftp.uploads).as("both tenants dispatch — neither blocks the other").hasSize(2);
        assertThat(reception301Calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("two tenants racing on the same FILE_SEQ_NO both dispatch exactly once")
    void twoTenantsRacingOnSameFileSeqNo_bothDispatchOnce() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (String biz : new String[] {BIZ_KEY, OTHER_BIZ_KEY}) {
            Thread t = new Thread(() -> {
                try {
                    go.await(5, TimeUnit.SECONDS);
                    service.receive(TRAN_DT, biz, FILE_SEQ_NO);
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }, "h4-tenant-" + biz);
            t.setDaemon(true);
            t.start();
        }
        go.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();

        // Tenant isolation must hold under concurrency too — 2 uploads, not 1 (over-blocking) and
        // not 3+ (a leaked claim).
        assertThat(sftp.uploads).hasSize(2);
        assertThat(reception301Calls.get()).isEqualTo(2);
    }

    // ── harness ──────────────────────────────────────────────────────────────

    /** Release {@code n} threads simultaneously into {@code body}; fail the test on any throw. */
    private void runRace(int n, Runnable body) throws Exception {
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                    body.run();
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }, "h4-race-" + i);
            t.setDaemon(true);
            t.start();
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).as("all threads started").isTrue();
        go.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).as("all threads finished").isTrue();
        assertThat(errors).as("no driver threw").isEmpty();
    }

    /** Pass-through transaction manager — the per-split-file template runs inline, no DB. */
    private static final class NoOpTransactionManager
            implements org.springframework.transaction.PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {
            // no-op
        }
    }

    private static TbPayFileMst payRow(String bizKey, String procStat) {
        TbPayFileMst p = new TbPayFileMst();
        p.setTranDt(TRAN_DT);
        p.setBizKey(bizKey);
        p.setFileSeqNo(FILE_SEQ_NO);
        p.setOutBankCd(OUT_BANK);
        p.setWdrlActNo(WDRL);
        p.setBankPrtnId("WRB001");
        p.setFundTp("O");
        p.setTranTp("1");
        p.setProcStat(procStat);
        return p;
    }

    private static TbPayFileData detail() {
        TbPayFileData d = new TbPayFileData();
        d.setSeq(BigDecimal.ONE);
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setFincMdaNo("0011001234567");
        d.setTarBankCd("970436");
        d.setTranAmt(new BigDecimal("1000000"));
        d.setAntDivCd("C");
        d.setFincMdaOwnerNm("NGUYEN VAN A");
        return d;
    }

    private static TbFirmConf firmConf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd(OUT_BANK);
        c.setWdrlActNo(WDRL);
        c.setVanOrgCd(VAN);
        c.setFileType("WRB");
        c.setBankPrtnId("WRB001");
        c.setBatTranBranchDiv("N");
        return c;
    }
}
