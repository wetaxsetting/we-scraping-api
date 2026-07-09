package com.kosign.wecms.domain.payment.cardtransactions.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CardTransactionsFileStorageProperties} so the
 * {@code wecms.card-transactions.storage-root} value binds. Mirrors
 * {@code ContactsConfig} / {@code ErpTrnsHistoryConfig} (audit G3/G5).
 */
@Configuration
@EnableConfigurationProperties(CardTransactionsFileStorageProperties.class)
public class CardTransactionsFileConfig {
}
