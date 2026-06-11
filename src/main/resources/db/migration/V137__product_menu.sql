-- product_menu definition

-- Drop table

-- DROP TABLE product_menu;

CREATE TABLE product_menu (
	product_type varchar(1) NOT NULL,
	menu_item_id varchar(100) NOT NULL,
	disp_ord int4 DEFAULT 0 NOT NULL,
	CONSTRAINT pk_product_menu PRIMARY KEY (product_type, menu_item_id)
);
CREATE INDEX ix_product_menu_product ON product_menu USING btree (product_type);
