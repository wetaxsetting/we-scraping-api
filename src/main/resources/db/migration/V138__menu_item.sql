-- menu_item definition

-- Drop table

-- DROP TABLE menu_item;

CREATE TABLE menu_item (
	menu_item_id varchar(100) NOT NULL,
	parent_id varchar(100) NULL,
	menu_dep int4 DEFAULT 0 NOT NULL,
	route varchar(200) NULL,
	icon varchar(100) NULL,
	show_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL,
	permission_cd varchar(100) NULL,
	legacy_mnu_id varchar(100) NULL,
	legacy_wsvc_id varchar(100) NULL,
	CONSTRAINT pk_menu_item PRIMARY KEY (menu_item_id)
);
CREATE INDEX ix_menu_item_parent ON menu_item USING btree (parent_id);
