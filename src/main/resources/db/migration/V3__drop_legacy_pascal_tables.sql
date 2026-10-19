-- PascalCase / 구 StandardImpl 유령 테이블 제거.
-- 정본은 CamelCaseToUnderscores snake_case (artist, unit, unit_member, …).

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `Artist`;
DROP TABLE IF EXISTS `Unit`;
DROP TABLE IF EXISTS `UnitMember`;
DROP TABLE IF EXISTS `Chemistry`;
DROP TABLE IF EXISTS `Album`;
DROP TABLE IF EXISTS `Track`;
DROP TABLE IF EXISTS `Article`;
DROP TABLE IF EXISTS `Comment`;
DROP TABLE IF EXISTS `Shop`;
DROP TABLE IF EXISTS `ShopImage`;
DROP TABLE IF EXISTS `Schedule`;
DROP TABLE IF EXISTS `Archive`;
DROP TABLE IF EXISTS `Media`;
DROP TABLE IF EXISTS `Members`;

-- 미사용 레거시 (소문자)
DROP TABLE IF EXISTS `article_like`;
DROP TABLE IF EXISTS `members`;

SET FOREIGN_KEY_CHECKS = 1;
