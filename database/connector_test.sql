CREATE DATABASE `connector_test` DEFAULT CHARACTER SET utf8mb4;

USE `connector_test`;

DROP TABLE IF EXISTS `all_types`;
CREATE TABLE `all_types`(
  `bit` BIT NOT NULL,
  `bit_null` BIT NULL,
  `tinyint` TINYINT NOT NULL,
  `tinyint_null` TINYINT NULL,
  `tinyint_unsigned` TINYINT unsigned NOT NULL,
  `tinyint_unsigned_null` TINYINT unsigned NULL,
  `smallint` SMALLINT NOT NULL,
  `smallint_null` SMALLINT NULL,
  `smallint_unsigned` SMALLINT unsigned NOT NULL,
  `smallint_unsigned_null` SMALLINT unsigned NULL,
  `mediumint` MEDIUMINT NOT NULL,
  `mediumint_null` MEDIUMINT NULL,
  `int` INT NOT NULL,
  `int_null` INT NULL,
  `int_unsigned` INT unsigned NOT NULL,
  `int_unsigned_null` INT unsigned NULL,
  `bigint` BIGINT NOT NULL,
  `bigint_null` BIGINT NULL,
  `bigint_unsigned` BIGINT unsigned NOT NULL,
  `bigint_unsigned_null` BIGINT unsigned NULL,
  `float` FLOAT NOT NULL,
  `float_null` FLOAT NULL,
  `double` DOUBLE NOT NULL,
  `double_null` DOUBLE NULL,
  `decimal` DECIMAL(10, 2) NOT NULL,
  `decimal_null` DECIMAL(10, 2) NULL,
  `date` DATE NOT NULL,
  `date_null` DATE NULL,
  `time` TIME NOT NULL,
  `time_null` TIME NULL,
  `datetime` DATETIME NOT NULL,
  `datetime_null` DATETIME NULL,
  `timestamp` TIMESTAMP NOT NULL,
  `timestamp_null` TIMESTAMP NULL,
  `year` YEAR NOT NULL,
  `year_null` YEAR NULL,
  `char` CHAR(10) NOT NULL,
  `char_null` CHAR(10) NULL,
  `varchar` VARCHAR(10) NOT NULL,
  `varchar_null` VARCHAR(10) NULL,
  `binary` BINARY(10) NOT NULL,
  `binary_null` BINARY(10) NULL,
  `varbinary` VARBINARY(10) NOT NULL,
  `varbinary_null` VARBINARY(10) NULL,
  `tinyblob` TINYBLOB NOT NULL,
  `tinyblob_null` TINYBLOB NULL,
  `blob` BLOB NOT NULL,
  `blob_null` BLOB NULL,
  `mediumblob` MEDIUMBLOB NOT NULL,
  `mediumblob_null` MEDIUMBLOB NULL,
  `longblob` LONGBLOB NOT NULL,
  `longblob_null` LONGBLOB NULL,
  `tinytext` TINYTEXT NOT NULL,
  `tinytext_null` TINYTEXT NULL,
  `text` TEXT NOT NULL,
  `text_null` TEXT NULL,
  `mediumtext` MEDIUMTEXT NOT NULL,
  `mediumtext_null` MEDIUMTEXT NULL,
  `longtext` LONGTEXT NOT NULL,
  `longtext_null` LONGTEXT NULL,
  `enum` ENUM('a', 'b', 'c') NOT NULL,
  `enum_null` ENUM('a', 'b', 'c') NULL,
  `set` SET('a', 'b', 'c') NOT NULL,
  `set_null` SET('a', 'b', 'c') NULL,
  `json` JSON NOT NULL,
  `json_null` JSON NULL,
  `geometry` GEOMETRY NOT NULL,
  `geometry_null` GEOMETRY NULL,
  `point` POINT NOT NULL,
  `point_null` POINT NULL,
  `linestring` LINESTRING NOT NULL,
  `linestring_null` LINESTRING NULL,
  `polygon` POLYGON NOT NULL,
  `polygon_null` POLYGON NULL,
  `multipoint` MULTIPOINT NOT NULL,
  `multipoint_null` MULTIPOINT NULL,
  `multilinestring` MULTILINESTRING NOT NULL,
  `multilinestring_null` MULTILINESTRING NULL,
  `multipolygon` MULTIPOLYGON NOT NULL,
  `multipolygon_null` MULTIPOLYGON NULL,
  `geometrycollection` GEOMETRYCOLLECTION NOT NULL,
  `geometrycollection_null` GEOMETRYCOLLECTION NULL
);

INSERT INTO `all_types` VALUES (
  b'1',
  NULL,
  127,
  NULL,
  255,
  NULL,
  32767,
  NULL,
  65535,
  NULL,
  8388607,
  NULL,
  2147483647,
  NULL,
  4294967295,
  NULL,
  9223372036854775807,
  NULL,
  18446744073709551615,
  NULL,
  3.4028234663852886e+38,
  NULL,
  1.7976931348623157e+308,
  NULL,
  9999999.99,
  NULL,
  '2020-01-01',
  NULL,
  '12:34:56',
  NULL,
  '2020-01-01 12:34:56',
  NULL,
  '2020-01-01 12:34:56',
  NULL,
  2020,
  NULL,
  'char',
  NULL,
  'varchar',
  NULL,
  'binary',
  NULL,
  'varbinary',
  NULL,
  'tinyblob',
  NULL,
  'blob',
  NULL,
  'mediumblob',
  NULL,
  'longblob',
  NULL,
  'tinytext',
  NULL,
  'text',
  NULL,
  'mediumtext',
  NULL,
  'longtext',
  NULL,
  'a',
  NULL,
  'a,b',
  NULL,
  '{"a": 1}',
  NULL,
  ST_GeomFromText('POINT(1 1)'),
  NULL,
  ST_GeomFromText('POINT(1 1)'),
  NULL,
  ST_GeomFromText('LINESTRING(0 0,1 1)'),
  NULL,
  ST_GeomFromText('POLYGON((0 0,1 0,1 1,0 1,0 0))'),
  NULL,
  ST_GeomFromText('MULTIPOINT(0 0,1 1)'),
  NULL,
  ST_GeomFromText('MULTILINESTRING((0 0,1 1))'),
  NULL,
  ST_GeomFromText('MULTIPOLYGON(((0 0,1 0,1 1,0 1,0 0)))'),
  NULL,
  ST_GeomFromText('GEOMETRYCOLLECTION(POINT(1 1))'),
  NULL
);

CREATE TABLE `transaction_test`(`c1` BIGINT NOT NULL);

CREATE TABLE `tax` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `value` DOUBLE NOT NULL, `start_date` DATE NOT NULL);
INSERT INTO `tax` (`value`, `start_date`) VALUES (0.05, '2020-01-01'), (0.08, '2020-02-01'), (0.1, '2020-03-01');

DELIMITER //
CREATE PROCEDURE proc1()
BEGIN
SELECT VERSION();
END;
//

CREATE PROCEDURE proc2(IN param INT)
BEGIN
SELECT param;
END;
//

CREATE PROCEDURE proc3(IN param1 INT, IN param2 VARCHAR(8))
BEGIN
SELECT param1, param2;
END;
//

CREATE PROCEDURE proc4(OUT param1 INT, OUT param2 VARCHAR(8))
BEGIN
  SET param1 = -1;
  SET param2 = 'hello';
END;
//

CREATE PROCEDURE demoSp(IN inputParam VARCHAR(255), INOUT inOutParam INT)
BEGIN
    DECLARE z INT;
    SET z = inOutParam + 1;
    SET inOutParam = z;

SELECT inputParam;

SELECT CONCAT('zyxw', inputParam);
END
//

CREATE FUNCTION func1()
  RETURNS INT DETERMINISTIC
BEGIN
RETURN -1;
END;
//

CREATE FUNCTION func2()
  RETURNS VARCHAR(12) DETERMINISTIC
BEGIN
RETURN 'hello, world';
END;
//

CREATE FUNCTION getPrice(price int)
  RETURNS INT DETERMINISTIC
BEGIN
  declare tax DOUBLE DEFAULT 0.1;

  SELECT VALUE INTO tax
  from tax
  WHERE start_date <= current_date
  ORDER BY start_date DESC
  LIMIT 1;

  RETURN TRUNCATE(price + (price * tax), 0);
END;
//
DELIMITER ;

CREATE TABLE `privileges_table` (
  `c1` INT NOT NULL PRIMARY KEY,
  `c2` INT NOT NULL,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

GRANT SELECT, INSERT ON `connector_test`.`privileges_table` TO 'ldbc'@'%';
GRANT SELECT(`c1`, `c2`), INSERT(`c1`, `c2`) ON `connector_test`.`privileges_table` TO 'ldbc'@'%';
