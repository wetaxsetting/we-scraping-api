-- menu_item_translation definition

-- Drop table

-- DROP TABLE menu_item_translation;

CREATE TABLE menu_item_translation (
	product_type varchar(1) NOT NULL,
	menu_item_id varchar(100) NOT NULL,
	lang_cd varchar(2) NOT NULL,
	"label" varchar(200) NOT NULL,
	CONSTRAINT pk_menu_item_translation PRIMARY KEY (product_type, menu_item_id, lang_cd)
);
