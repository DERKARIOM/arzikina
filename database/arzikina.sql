-- phpMyAdmin SQL Dump
-- version 5.2.2
-- https://www.phpmyadmin.net/
--
-- Hôte : 127.0.0.1:3306
-- Généré le : lun. 05 oct. 2026 à 16:49
-- Version du serveur : 11.8.9-MariaDB-log
-- Version de PHP : 7.2.34

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de données : `u883737194_arzikina`
--

-- --------------------------------------------------------

--
-- Structure de la table `accounts`
--

CREATE TABLE `accounts` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `icon` varchar(64) NOT NULL,
  `color_argb` bigint(20) NOT NULL,
  `currency_code` char(3) NOT NULL,
  `initial_balance_minor` bigint(20) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT 'CASH',
  `card_last_four_digits` varchar(4) DEFAULT NULL,
  `card_expiry_month` tinyint(4) DEFAULT NULL,
  `card_expiry_year` smallint(6) DEFAULT NULL,
  `is_excluded_from_statistics` tinyint(1) NOT NULL DEFAULT 0,
  `mobile_money_package_name` varchar(191) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1,
  `display_order` int(11) NOT NULL DEFAULT 0,
  `savings_target_amount` bigint(20) DEFAULT NULL,
  `savings_description` text DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `accounts`
--

INSERT INTO `accounts` (`id`, `user_id`, `name`, `icon`, `color_argb`, `currency_code`, `initial_balance_minor`, `type`, `card_last_four_digits`, `card_expiry_month`, `card_expiry_year`, `is_excluded_from_statistics`, `mobile_money_package_name`, `created_at`, `updated_at`, `deleted_at`, `version`, `display_order`, `savings_target_amount`, `savings_description`) VALUES
('16548528-c345-4122-90a2-279dbdbe26ea', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Banque', 'BANK', 4278217807, 'XOF', 0, 'BANK', NULL, NULL, NULL, 0, NULL, 1787923504031, 0, NULL, 1, 13, NULL, NULL),
('31110101-1485-4c31-a798-42d699b5de65', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Maman', 'WALLET', 4283198271, 'XOF', 10000000, 'MOBILE_MONEY', NULL, NULL, NULL, 1, 'com.iisoft.tm.myNita', 1786570797319, 1789845037360, NULL, 5, 4, NULL, NULL),
('3a9ee996-3f36-48e4-865c-4a1a83ac980f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Mobile Money', 'MOBILE_MONEY', 4294286859, 'XOF', 0, 'MOBILE_MONEY', NULL, NULL, NULL, 0, NULL, 1787923504031, 0, NULL, 1, 14, NULL, NULL),
('44c112d3-4787-4ad6-b9c5-93649aaff584', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'MyNita', 'MOBILE_MONEY', 4284773515, 'XOF', 228700, 'MOBILE_MONEY', NULL, NULL, NULL, 0, 'com.iisoft.tm.myNita', 1786570797319, 1789845037358, NULL, 6, 3, NULL, NULL),
('58d0b718-d6c5-46ed-b534-06366e1cf252', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Mariage', 'BANK', 4293675161, 'XOF', 0, 'SAVINGS_GOAL', NULL, NULL, NULL, 1, NULL, 1786828025190, 1790328767523, NULL, 4, 6, 100000000, 'Pour la préparation de mon mariage'),
('63a53de8-284c-4e22-8e32-6f29ea237930', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Visa Amanata', 'CREDIT_CARD', 4279286145, 'XOF', 550900, 'CREDIT_CARD', '0272', 5, 2029, 0, NULL, 1786882106207, 1788947053352, NULL, 3, 0, NULL, NULL),
('8495ad54-cdb7-4ab1-81de-28aad688fd80', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Espèces', 'CASH', 4279673674, 'XOF', 0, 'CASH', NULL, NULL, NULL, 0, NULL, 1787923504031, 1788018460509, 1788018460509, 2, 15, NULL, NULL),
('86f251b7-2294-4d28-b169-debcf9ed0b77', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Amanata', 'MOBILE_MONEY', 4279548070, 'XOF', 7100, 'MOBILE_MONEY', NULL, NULL, NULL, 0, 'com.iisoft.tm.appamana', 1786570797319, 1788946193458, NULL, 5, 2, NULL, NULL),
('93ba5b0b-9aea-4f83-a6c0-9aeea2d110d0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Compte Postman', 'CASH', -16776961, 'XOF', 100000, 'CASH', NULL, NULL, NULL, 0, NULL, 1787742917000, 1787818439309, 1787818439309, 2, 11, NULL, NULL),
('9644acef-eea8-4d0f-90e2-3acb2df21523', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Wallet', 'WALLET', 4283198271, 'XOF', 0, 'CASH', NULL, NULL, NULL, 0, NULL, 1787923504031, 0, NULL, 1, 16, NULL, NULL),
('97d3f709-8069-4963-8c32-874009967823', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'test', 'BANK', 4293870660, 'XOF', 585800, 'CASH', NULL, NULL, NULL, 0, NULL, 1790456494349, 1790492906896, NULL, 1, 0, NULL, NULL),
('a698ded3-c4ec-41a7-b0ef-141c756add1c', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'test@gmail.com', 'CASH', 4279286145, 'EUR', 0, 'CASH', NULL, NULL, NULL, 0, NULL, 1790808458111, 1790808534415, NULL, 1, 1, NULL, NULL),
('aafbfdfb-47ec-4924-91dd-b7698277468a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Wave', 'CASH', 4282090230, 'XOF', 0, 'MOBILE_MONEY', NULL, NULL, NULL, 0, 'com.wave.personal', 1787223922047, 1788946173605, NULL, 3, 1, NULL, NULL),
('ae31ee81-7149-494e-94c5-ecbc31ed8e31', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Test', 'CASH', 4279286145, 'XOF', 0, 'MOBILE_MONEY', NULL, NULL, NULL, 0, NULL, 1790789723280, 1790797884426, 1790797884426, 2, 10, NULL, NULL),
('baf2e983-a62e-48ce-b6fc-557f114a72b0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Money Fusion', 'MOBILE_MONEY', 4282090230, 'XOF', 3500, 'MOBILE_MONEY', NULL, NULL, NULL, 0, 'com.moneyfusion', 1786906691023, 1788946233163, NULL, 5, 5, NULL, NULL),
('ce3ba859-001c-41d6-b65e-32c016eeb52e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Espèces', 'CASH', 4279286145, 'XOF', 692500, 'CASH', NULL, NULL, NULL, 0, NULL, 1786570797319, 1789116759178, NULL, 4, 0, NULL, NULL),
('d889a24c-6f24-432a-b174-5d16e7190b19', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Épargne', 'SAVINGS', 4278232440, 'XOF', 0, 'SAVINGS', NULL, NULL, NULL, 0, NULL, 1787923504031, 0, NULL, 1, 17, NULL, NULL),
('e5628b04-0a6d-4fc9-805d-33e25b17e901', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Mon Compte', 'CASH', -12404328, 'XOF', 1500000, 'CASH', NULL, NULL, NULL, 1, NULL, 1787846387572, 1787846469920, 1787846469920, 2, 12, NULL, NULL),
('ebd3d4a0-b666-4194-87f0-f452bd89de66', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Compte Postman', 'CASH', -16776961, 'XOF', 100000, 'CASH', NULL, NULL, NULL, 0, NULL, 1787742237000, 1787818427605, 1787818427605, 2, 10, NULL, NULL),
('f187ccf7-40f5-4ad4-8bf0-a174bb2cbb80', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Ecobank', 'CREDIT_CARD', 4279286145, 'XOF', 0, 'CREDIT_CARD', '7046', 10, 2027, 0, NULL, 1786899176049, 1788947053364, NULL, 3, 1, NULL, NULL),
('fc1a79b8-cc49-42fc-9fb2-61b07e24a7be', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Visa M.Fusion', 'CREDIT_CARD', 4284704497, 'USD', 5, 'CREDIT_CARD', '6264', 4, 2029, 1, NULL, 1786906948914, 1788947025267, NULL, 2, 2, NULL, NULL);

-- --------------------------------------------------------

--
-- Structure de la table `auth_tokens`
--

CREATE TABLE `auth_tokens` (
  `id` bigint(20) NOT NULL,
  `user_id` char(36) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `device_id` varchar(191) DEFAULT NULL,
  `device_label` varchar(191) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `expires_at` bigint(20) NOT NULL,
  `revoked_at` bigint(20) DEFAULT NULL,
  `last_used_at` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `auth_tokens`
--

INSERT INTO `auth_tokens` (`id`, `user_id`, `token_hash`, `device_id`, `device_label`, `created_at`, `expires_at`, `revoked_at`, `last_used_at`) VALUES
(1, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '92a70fd23fd4fdccc1b5d6ef2b7d0dc68fde1f38303fb1c51d7c9fa3d23f4282', '6d1d009bb22acb63', NULL, 1787650885939, 1790242885939, NULL, 1787652038920),
(2, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'de57e929dfb6319e2cfaf86ba7f177268d1ee9e3e4235336cde8308af5fafad7', '6d1d009bb22acb63', NULL, 1787652064475, 1790244064475, NULL, 1787654497755),
(3, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'adb115ddec6e14330894aa0c4479f1e318981537943d4779d60eae008a6741c2', '6d1d009bb22acb63', NULL, 1787654646977, 1790246646977, NULL, 1787655829708),
(4, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c711cd702c02e82269cf41c2c48fbde51d389f32433a1ddac7e0b052f0dec344', '6d1d009bb22acb63', NULL, 1787655896439, 1790247896439, NULL, 1787655912057),
(5, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e3fc00559fee66d63e8ade173cefe91d2097b241afa260d6dc3875e13ab44c08', 'postman-test-device', 'Postman', 1787656699524, 1790248699524, NULL, 1787656742959),
(6, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '68f9aa99f1fcf4c14c5b8bcf7b1c512a53be5480a6d14822fe3cbc5143022a97', 'postman-debug', 'Postman (debug manuel)', 1787657031003, 1790249031003, NULL, 1787657350076),
(7, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'efaf6f83534ac8656ec35a18c1a06d3d8d05f7c53793eb54d146e922daf3a94d', '6d1d009bb22acb63', NULL, 1787657264609, 1790249264609, NULL, 1787690747511),
(8, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '88b206e083f964d51f2af4c9250949757083a3acb0e4f1f439f401ac1acbd088', '6d1d009bb22acb63', NULL, 1787691428183, 1790283428183, NULL, 1787693139710),
(9, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '543ebdac7aac4f09709944272e943b6aec4b1b326e2be6460f664b35d764234a', '6d1d009bb22acb63', NULL, 1787693165731, 1790285165731, NULL, 1787694372188),
(10, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dbf4e10f3e9f4df67aaa0ceba2cdda72c54d32cc43c02e2c614fa7b18ff89ba3', '6d1d009bb22acb63', NULL, 1787694451578, 1790286451578, NULL, 1787722281315),
(11, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '076a1f18af474e0f6f30e8d44f52e1a45dd541f7b5bc19e0af07552cdabe1299', 'postman-debug', 'Postman (debug manuel)', 1787695029491, 1790287029491, NULL, 1787695049251),
(12, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6919d9637b7da8e4fcb46c04ffb96b84d1956f20f0802ff6afb8be9c3c03d3fe', '6d1d009bb22acb63', NULL, 1787722303843, 1790314303843, NULL, 1787740780606),
(13, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '45f000012e91b5074bd702c25342cf076fcc8605f501a903dde315efbcebc9dc', '6d1d009bb22acb63', NULL, 1787740804473, 1790332804473, NULL, 1787741277435),
(14, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e7c4149db335a52dea6076b97600ab0a5f6ad51f24a331c1c7bb01e81385e687', '6d1d009bb22acb63', NULL, 1787741297150, 1790333297150, NULL, 1787741612870),
(15, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b93b1506e758804e0160a2fd128b5dc3c9c64edb9a7028ac6fac0fd91e1986ce', '6d1d009bb22acb63', NULL, 1787741627851, 1790333627851, NULL, 1787741755984),
(16, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5f46cf8d7e10b161d9a62835f9520f5f50fbde581676929887d653779d2d911a', '6d1d009bb22acb63', NULL, 1787741815233, 1790333815233, NULL, 1787744186568),
(17, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '87cb5c7a21bf6da90b1e27c075703be12a4abdd7e6d98d82963d1cf8867f0955', 'postman-test-device', 'Postman', 1787742195312, 1790334195312, NULL, 1787742951097),
(18, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bee159377268a550294e3af97ca70154e1d8e856c479c81f2bbb470277712895', 'postman-test-device', 'Postman', 1787743423187, 1790335423187, NULL, 1787743500239),
(19, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5d1ec70a92ec8c578e5b4d8fd48ef61f025ac5ff7cf4040bc8a387616ee806d2', '6d1d009bb22acb63', NULL, 1787744531291, 1790336531291, NULL, 1787745811242),
(20, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2b263d7749bf5ecf732dc54ece8ee3a84a3773e6d18410508de75559eb60a42d', '6d1d009bb22acb63', NULL, 1787745832955, 1790337832955, NULL, 1787754392418),
(21, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'daee531e7802911a56e9e3f99979316b0ac82c514b1015398d097a14b6d7ab4a', '6d1d009bb22acb63', NULL, 1787754408748, 1790346408748, NULL, 1787756781728),
(22, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '549ae91df5da177b558f6b402f5e16fac56e818d658c58327d6dd97d95fa2b00', '6d1d009bb22acb63', NULL, 1787756821777, 1790348821777, NULL, 1787762233144),
(23, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '23dee1f05dc3c4c72fb8b38cf77112865f62d0ecb648d4b29ed5a385be376e18', '6d1d009bb22acb63', NULL, 1787762251617, 1790354251617, NULL, 1787769259513),
(24, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a5be8efcfdebd94f93bf2127de2bff074aa98c8cc08937b8acb93e0f2f97799e', '6d1d009bb22acb63', NULL, 1787772125014, 1790364125014, NULL, NULL),
(25, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a4c6103ae960c713bb99fb71c0a69c0a8f1369180122b330d61d8ab23ee5aa68', '6d1d009bb22acb63', NULL, 1787772138386, 1790364138386, NULL, 1787776938817),
(26, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0860761bb294422d6249b6fe50996306ff84d98506e51b9c8fc9194eab92edca', '6d1d009bb22acb63', NULL, 1787776986853, 1790368986853, NULL, 1787777815396),
(27, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '18275c0daf54ff54e5114f3644bf60e0ac35ce6a3aa42fc4f6319bd0dee430d6', '6d1d009bb22acb63', NULL, 1787777927881, 1790369927881, NULL, 1787778793115),
(28, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c88c6af14b7ccf67c94891d12b59b5d8f62673d2d6192b51bfa3433cecb3cf54', 'postman-test-device', 'Postman', 1787778614234, 1790370614234, NULL, 1787781437873),
(29, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7ee4a8669fccbf89ffbc28ca8c7108128e59a3272dd15609d1a61917fd6d03ad', '6d1d009bb22acb63', NULL, 1787778853860, 1790370853860, NULL, 1787780734737),
(30, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b70835b5c3dbbe795f64c3f7a5ee12a94c4e34cfe8352a90deb72d752ee9b682', '6d1d009bb22acb63', NULL, 1787780747635, 1790372747635, NULL, 1787922473025),
(31, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '109bd02a03f62658bcebc9749f672f2221bd09e09574b41337baa21987ec56b8', 'postman-test-device', 'Postman', 1787825716831, 1790417716831, NULL, NULL),
(32, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '68a64611d890f8c816f0205faa81e8adc29611ca006380824bcda16ee83e3da2', NULL, NULL, 1787830697476, 1790422697476, NULL, NULL),
(33, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '26e2bdf1e3ff95efb48ac92509816adb4780e665acf31d160ecdedbc697ade4d', 'c0fbeb77-2da1-49f3-a5c1-661b3184eccc', 'Navigateur — Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.7390.37 Safari/537.36', 1787830986790, 1790422986790, NULL, 1787830994207),
(34, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5bbc3c04229efcf17f4e6c385e730389c370ab79d6c9468f9ed7f8ff390611a5', 'ad42b43b-c5f6-4533-8be8-562808d4825c', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787831052808, 1790423052808, NULL, 1787832028010),
(35, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '15801c331e7194d27ade10e57259b1378b451a35cb7305e9cfcef4efa075c725', NULL, NULL, 1787832591644, 1790424591644, NULL, NULL),
(36, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6914f0a739e64dab8404a8210deb3ae4c62e5568635c9486e7c7033c6ad2a01a', NULL, NULL, 1787833062427, 1790425062427, NULL, NULL),
(37, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '33698e754a9eb426d038277c7526da2b2885c03af12686af387df8bc878c86a1', NULL, NULL, 1787833124487, 1790425124487, NULL, NULL),
(38, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c1c787acec003f3b737dc26cde67d731e3cb9b643bae0b1ac3afc0c91fa01802', NULL, NULL, 1787833130895, 1790425130895, NULL, NULL),
(39, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7f1553a6e875c32b10dad5e2a2f28c85d6cadaa36bca67cdb37b756fcd3b8b50', NULL, NULL, 1787834840335, 1790426840335, NULL, NULL),
(40, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7e1bb77a13288ef08684160184d6576ab4c3f0eacf58966cded7696381b4ff16', NULL, NULL, 1787835021289, 1790427021289, NULL, NULL),
(41, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7f9ca1e87f6af14d29138a2adcad2b29bf883102ead6c63f1faa047e22052746', NULL, NULL, 1787835994597, 1790427994597, NULL, NULL),
(42, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b5918c3082ca572a71dee56a70130b42ba519614c84062cc2ea4536ffb794134', NULL, NULL, 1787836054276, 1790428054276, NULL, NULL),
(43, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e02432ed7b5fde855109330c135f5ecd14e7258d1cdbf76967b8bf3f12edaf38', NULL, NULL, 1787836054993, 1790428054993, NULL, NULL),
(44, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4ec0fac3c2fe76b525f68fe44f9cf0d126997281d787cf2f353545628682a3b1', NULL, NULL, 1787836060836, 1790428060836, NULL, 1787836061696),
(45, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6712d2bb72f2f644ee6c5813ede3bb3f3ce5b9c231c241648a3f6c95fd11a155', NULL, NULL, 1787836066293, 1790428066293, NULL, 1787836066856),
(46, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '44a3c79c8b27ac5c76ef281275fd7bf4f8b1971afd7c5d900dafd4bd71c14a5b', NULL, NULL, 1787836074416, 1790428074416, NULL, 1787836077320),
(47, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dd165635752c6e6bf4a204e81a67d9aba12130a93d566ff4d3006ca9f975da67', NULL, NULL, 1787836084717, 1790428084717, NULL, 1787836090489),
(48, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dce27c76d4ec072637da2678b648762095d0bb81437b586f2203eb13fb58d568', NULL, NULL, 1787836095955, 1790428095955, NULL, 1787836104194),
(49, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2acbc8522e07da121d4d01f858d41cabace9ba3b84c00aa0bc031471e61eccb9', NULL, NULL, 1787836111277, 1790428111277, NULL, 1787836119424),
(50, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd9a3f7dc1392f2e1a79dcfede9e6cfcf648a5279ca7e8b5888bcf5acf8d70f94', NULL, NULL, 1787836124836, 1790428124836, NULL, 1787836127649),
(51, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7e72974ed806e3d5ed03426f04a3cf6562eff03c7d5a4daa6ab9901e60998b96', '70dda6a4-3b30-4ce1-8bc9-ccc042eef4e5', 'Navigateur — Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.7390.37 Safari/537.36', 1787836230952, 1790428230952, NULL, 1787836238540),
(52, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0971e3b33bc2f8b438659cca858a102f46e4f21ae19e36edf17bbc22d8cf06ad', NULL, NULL, 1787838781074, 1790430781074, NULL, NULL),
(53, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b3ec747a43600af656df17e1e34e20bc259dbea240262a5dfc367298d0ad0942', '395f7dd4-77a4-4a2c-b4b0-bf844d122193', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787838838011, 1790430838011, NULL, 1787840039141),
(54, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c4e9ba013956b3d21ad938081e1ebd17982b2c15bcfd71c6eb45e432cd1d5639', '325b95fd-ed2b-41c6-b53f-9bf1d4b86788', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787838974296, 1790430974296, NULL, 1787839139894),
(55, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3002e2f75813cb50bcab05fb9e48ed2a0958dd3942190098e0ec2811c9669b8d', '325b95fd-ed2b-41c6-b53f-9bf1d4b86788', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787839170275, 1790431170275, NULL, 1787869708304),
(56, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '719817e7b89d3f9ac9b3083e07328687e18531fe30b5436d1238e7a3f0c27418', 'ced75b17-5916-48fa-a6d2-11e62aff90d7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787840096893, 1790432096893, NULL, 1787845215034),
(57, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7a2120fcfc7611edfc6d682c496bbbbf8bdf03b962d219d74b498d8918491f53', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787843556634, 1790435556634, NULL, 1787846293059),
(58, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3a9d908d7d0d0910475903171ba262154730d898e0afde72f465fbac8a22f670', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787846305740, 1790438305740, NULL, 1787847533923),
(59, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '45340e16ad789f8724b2c1599e3ee31b7f1ec175eee8cbd2a811331659d7b6f5', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787847539539, 1790439539539, NULL, 1787848047460),
(60, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6294d87ca836cc9d0c0cb5dfec80e48c3f13bdf5b12a24d44ea8b63f8b05de58', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787848135317, 1790440135317, NULL, 1787848456243),
(61, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cd77f0f1d6ce7e96392b99ff49a28a8d64781224bd759629581a224f4cd86e3a', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787848463820, 1790440463820, NULL, 1787849585066),
(62, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '666132d28162e056b4562083ba962f0cfd66951b7e033e72e526453ad8e43e83', '754c940a-0e08-440f-a76b-46162e284bf5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787848969775, 1790440969775, NULL, 1787851992329),
(63, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fbd9948b45de995835cfebff2887d9d014c65999a415beeb4a01ba49b209d9cc', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787849591180, 1790441591180, NULL, 1787850432809),
(64, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a3f9c03fb29f9f1bddc3874132a6d83cc2ebbfb968b00b80c681dac56d0396dd', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787850439499, 1790442439499, NULL, 1787851079364),
(65, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '050ec44f4a6244b3ea8dc58a7dd2f4b2953642e536d75f832296265e87403b38', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787851086819, 1790443086819, NULL, 1787851878015),
(66, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8f4e99302104cc244f1975946f9b5d96fa9349e67e271182ef89632f0bccb6a8', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787851883198, 1790443883198, NULL, 1787870351305),
(67, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3cb8acca9d8fc09e59f4acd883245e4a6ad93ee57de7dea2914a71fddd526ffd', '325b95fd-ed2b-41c6-b53f-9bf1d4b86788', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787869714627, 1790461714627, NULL, 1787870262935),
(68, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0d89159647a1f2cb186b794121357848fc1da144e80e240f9b907bbca8a1e8ed', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787870359031, 1790462359031, NULL, 1787870806250),
(69, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '68ca9061e9b364a4592dcaf8803d6a711f6a72c8e9bbbda8be7b5f65c91e39d2', '988ee2dc-f000-4abc-a950-ec8d04f45603', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787870733947, 1790462733947, NULL, 1787871692003),
(70, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '49a2a7a289ef913f32cf8e757780e6aca76aa2620f09da4c2823098016e2c4da', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787870811965, 1790462811965, NULL, 1787871718131),
(71, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ce506a472f98453c5314b4163449b907f3e5cd40bccd51eee87c67c3a61b4e6e', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787871725907, 1790463725907, NULL, 1787872481091),
(72, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fdcabe9fcd338723f8cce716fe9a0522be307697d35c72b84e77e0162845980c', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787872492524, 1790464492524, NULL, 1787872861718),
(73, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8e9e1d17646ecee5f3da0f1d12f759b76f8cbebf3608a097961bf1a13b2e2ec9', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787872870106, 1790464870106, NULL, 1787906202630),
(74, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fb8b58a9d0cd1ab0172adaba81fe0ae3bf120a9d9487abc1eebc560f0a4269ec', '07c49f26-b6c5-47d2-bcf0-d98512570f64', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787872964107, 1790464964107, NULL, 1787873625292),
(75, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '29f9a22188abe633d229a7019989fdbc455bd832f25186d4a29bb53f24c1f25f', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787873461963, 1790465461963, NULL, 1787906210363),
(76, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2543855a5f7ff33ae8c7e045d0338e41042d079a92b37406595c17d39de3f1e1', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787906215839, 1790498215839, NULL, 1787909218851),
(77, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a87018b1bfac0036c1f88dcc1b355e736d1044c0350545c5a03545b14137e03a', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787909222519, 1790501222519, NULL, 1787910591609),
(78, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4a2f9abcc414cb8bab3459147a231d73f77b0c80a08a4d535a333d45dcf69ca3', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787910596937, 1790502596937, NULL, 1787912644314),
(79, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '94532c71da1ab38465f229c0e6512935fd86765685422232035574ca0f1526c9', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787912690282, 1790504690282, NULL, 1787912731891),
(80, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '330aa86f3002743cccf02470690968fbd7b50e27a72e8cb251b96c3e42ce589a', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787912878077, 1790504878077, NULL, 1787913071736),
(81, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9c27b1965023af6a226b87a6b8e0606a5871fbfaf06371f337d850be66af2113', 'f1ac02b8-3350-4da8-8d89-bedced65da8b', 'Navigateur — Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.7390.37 Safari/537.36', 1787914190661, 1790506190661, NULL, 1787914197951),
(82, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cb0884e1f05a66b04c412d3f87f626fb77d5c5de43ea08acd3546b669d980fa3', 'e0496283-eb69-45c6-891f-01745ea5f88a', 'Navigateur — Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.7390.37 Safari/537.36', 1787914214575, 1790506214575, NULL, 1787914238793),
(83, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '87fd36a1fa473f7bbae7a88470913fdbe9e7ae8f79bf2c6a7ae645c74e42bf6a', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787914717915, 1790506717915, NULL, 1787914732945),
(84, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e479b5fa12ede92e5b0b7c59e0d2d3ba7b28fa03a63cd5dbcf881d96d64ed2c4', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787914746738, 1790506746738, NULL, 1787914759235),
(85, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '811a8b5ff61fdaf235e833f67d518482c0803a341cd595fa65da66580bdacdb2', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787915053357, 1790507053357, NULL, 1787915530546),
(86, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '80750f226d74864b3a1a50845679002b8ceca45ba5de115addc37dd1fd19828c', '25de274b-afc8-4399-8b17-d77732ce0809', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787922998552, 1790514998552, NULL, 1787924087026),
(87, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f6309971e4e9e7530feebcf45312c6716678f5b87d47ddf57ddb36e5792ca583', '6d1d009bb22acb63', NULL, 1787923527173, 1790515527173, NULL, 1788017840431),
(88, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5c14264b2b173d21465a41cf0418571f0d260cebb8b3f1436881347961dbe2a6', 'f0295629-2515-4b36-9bb4-ebdbbcd65a1e', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36', 1787928967995, 1790520967995, NULL, 1787929382567),
(89, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c3222c520a558e3fcb0a65d0ddfed2c87915ee1590ecbeca6ca7640eddc1e3fa', 'f0295629-2515-4b36-9bb4-ebdbbcd65a1e', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36', 1787932147008, 1790524147008, NULL, 1787932347647),
(90, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5f6c59f7f90b12d9e50271f70bc074a57d574c57054c4f9b59d3cb118f94085e', 'f0295629-2515-4b36-9bb4-ebdbbcd65a1e', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36', 1787932359944, 1790524359944, NULL, 1787932626399),
(91, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f34419863921411f1cba269347eb9f0291d8289c53c88fdba62c48223e1586b4', 'f0295629-2515-4b36-9bb4-ebdbbcd65a1e', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36', 1787934530723, 1790526530723, NULL, 1787934531750),
(92, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3bca568b204f1c73320550532b5414290378bcc3fe8b18a92d7a5d0f6f7257f3', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787940175508, 1790532175508, NULL, 1787940217171),
(93, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2a30e741a810003440853e1afe299526496aa8d8d0dcf988b9bc1276c6739c7a', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787940228424, 1790532228424, NULL, 1787940229788),
(94, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fb9eabc3f4c4635dc043256551d26dc27b69ce68c45992992be348f6310a94f9', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787940484441, 1790532484441, NULL, 1787940565536),
(95, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2a641da0bcf6da44ccabfa0c7386676e8fc0717d704d84944af4e795ee3405f3', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787940596143, 1790532596143, NULL, 1787940597444),
(96, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8103527d9581f1debec4a45a4609de218dab7ea96b3290d56d92eb1b99f89251', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787940707228, 1790532707228, NULL, 1787940729924),
(97, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '69a3de9dad3f733a3a8588f9a5fb2ae2b2d3eafe4b2cac6264065b80b8a2eb70', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787940757608, 1790532757608, NULL, 1787940759381),
(98, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1b15873fcd854b46840582b7e2346a50c79cd0e2ace8b983d817e3cc2deb0472', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787999803136, 1790591803136, NULL, 1787999804522),
(99, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd9038cf32af6bfaaafab854d3ee7335193ce2b683397f9154309113bdd9bcaef', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1787999819573, 1790591819573, NULL, 1788000031619),
(100, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '371281d284b77fcc4290d10db6879aebce92344532072e65ae80100ab7eacd43', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788000664977, 1790592664977, NULL, 1788005267388),
(101, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '777041c1b0a2221e3b4e3d1bcdac5b761c9676f0f96625eeed99843cb3b61ab3', 'dd7b6d24-1f2d-4422-a451-dbf1bc69c882', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788018312317, 1790610312317, NULL, 1788018654138),
(102, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ddddca35a72866a2943a13c73eb70670b17437ecc2e8251900a267eadde27473', '6d1d009bb22acb63', NULL, 1788018415457, 1790610415457, NULL, 1788279877547),
(103, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '538d51312c42c01090d5318a84e9e7dafd05bc5c24be7ea7a588e80ff9e0e223', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788018648476, 1790610648476, NULL, 1788034108671),
(104, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a31ce5b8d278406cd75942c8446da641281ba53a07f6258df60d5da04bbbea3c', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788081676837, 1790673676837, NULL, 1788083117793),
(105, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'aeb4b4cbd337cc425f785644c84432440e0ca4686d9d1f26996537c7e93b45a8', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788083976235, 1790675976235, NULL, 1788085036626),
(106, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8fe50cbae37188a1f0773838eaeb497bbf2074e95bce1012722bc3f66fe37a7e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788126817987, 1790718817987, NULL, 1788164255684),
(107, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e46b4a4936f89949893732d75da2cc772fe01f6059a0578b6f264a45a35ba269', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788182188692, 1790774188692, NULL, 1788182353890),
(108, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1c720a3e75e8d7c79b835c0a38c914545b68534d420c82f99f07cf1da52596c6', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788182369897, 1790774369897, NULL, 1788184298914),
(109, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f96830c3aac435131c264edc4e0a57fe96ce8e35eb76ce1e36dc9505d2218d1f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788187603081, 1790779603081, NULL, 1788209344841),
(110, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a451ee492d9ae9991085580e958a4949979e84fdc0b899bd1cd64de3fbab2d35', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788209355034, 1790801355034, NULL, 1788209926055),
(111, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c5d949005a1155fc2ba21e0baccc318010907391c772fe839bc0dd6cd6e3a25e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788209949072, 1790801949072, NULL, 1788210235819),
(112, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e9cbfdcc2a4654be59b8b99a09f37f1ec6cae341112720594a0d2067fd79d00e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788210249033, 1790802249033, NULL, 1788211679701),
(113, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '43dd94e83a349289ba30d8cac873d9eadb39eefca2ffb715aaf7eb3540f03279', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788212018271, 1790804018271, NULL, 1788212706916),
(114, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7ec6e1286ddd92bb1ab12873ea6c265162f1284795707223ec4e2f17ae960d81', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788212718532, 1790804718532, NULL, 1788214571722),
(115, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fa70b34f0c544984049dc91ae8f7212935606dac1eae48832a3153abe4b8028f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788214583373, 1790806583373, NULL, 1788216478201),
(116, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fbf24460faef0285fb30b84069c20af60325d2e77ef92b5489b320d9d8c4e860', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788216484610, 1790808484610, NULL, 1788216898959),
(117, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ad1de311a03a9f554f1888053ffa3af9b4a9317385b2cedc66ee266544f21c4d', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788216913315, 1790808913315, NULL, 1788216967048),
(118, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c204c62a7e504f15839ffb0fb47cc3c8680759767738ad0165550cb06139875c', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788216986009, 1790808986009, NULL, 1788217027687),
(119, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '744ae212d5833aec0af38cef941cf1285b698c04cc27848238db63a281b4fa34', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788217039933, 1790809039933, NULL, 1788217081456),
(120, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'afb7583dad4170f151045122f9873ad0540125c2b380ab12c2008fa53c95335c', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788217514212, 1790809514212, NULL, 1788256378153),
(121, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c76e063c13d90f71f520f2e862ad54b9efdc09bfcd56a86f14a8b13057793d89', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788256387390, 1790848387390, NULL, 1788256969822),
(122, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c3052c0c2f8623f27b4c463fb1a5a375415c932537a03696c9435037e10b89e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788256977610, 1790848977610, NULL, 1788257768121),
(123, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3a5d6d7299ef3a719fcdc9098e626521274828e113bd743c65d16e21c923785b', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788257778290, 1790849778290, NULL, 1788258747578),
(124, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ca642641192ce9c8d46de7a228f9645365071f53b5706ae90ba17bff6ebac3c5', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788258760929, 1790850760929, NULL, 1788258862472),
(125, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b5c239267b722e5dfddd929484e7395c2ad5b98050b056a2ec9f0005736b85b4', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788258873205, 1790850873205, NULL, 1788259014874),
(126, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2f92da3d9f875a34e693db2c9a8c7f8bf6b44c6803801b1885fd07c9a5a13c14', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788263307474, 1790855307474, NULL, 1788264130917),
(127, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4905a50eaa3a29ae3ece975b66ddddb3109ff9e251e42c2bb3589dbb9fdf209a', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788264139371, 1790856139371, NULL, 1788265039098),
(128, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a0f804de1b5cd10fcf2df5ea0d7414fe7bd54dc3c714ff87e46da6c47f3683e3', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788265046647, 1790857046647, NULL, 1788268401117),
(129, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '10ad152427617aa69b498e6103b350a54ff1a88fd438bf56df5b4391f7a1c4aa', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788268408970, 1790860408970, NULL, 1788269250924),
(130, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '82b2f1baa148819ce01325a3e222b0f208bb4b6ea2dacd2d5cfa7c81ffe18983', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788269273391, 1790861273391, NULL, 1788269489035),
(131, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '344507556702ec4ea8e99ce438eb609966d86b859aeb5462822df578994a6d00', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788269500745, 1790861500745, NULL, 1788270609167),
(132, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '866f96aea6d51b17913502777a1297096b8e8aa47faec6617283c462838d0a8c', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788270618368, 1790862618368, NULL, 1788271261849),
(133, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fe16ed1201b1ebccfa6ccca967f932dd6eef68fe610d94241db068ab10bb27eb', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788271270167, 1790863270167, NULL, 1788271912194),
(134, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f7ec71100e3c6ee0060df4f1ce4d0fa6819403ff33014152fe482c8963ac4383', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788271934822, 1790863934822, NULL, 1788272256362),
(135, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '76a43822002c493f76ab12546c4bab8efe8b66420750894aeaab3cfdfde1acd7', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788272294668, 1790864294668, NULL, 1788293864469),
(136, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b43357ad0ff4ef313b5d6301fc1eda16c750ed423856bd12a209abb11c6012c2', '6d1d009bb22acb63', NULL, 1788279916058, 1790871916058, NULL, 1788282842883),
(138, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b4bbcd767eaf2cabcbafc20570293900f0875f46870de9d2ac4e88316dd729df', '6d1d009bb22acb63', NULL, 1788293512854, 1790885512854, NULL, NULL),
(139, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'db72d3b7c2eb2fa73431978e60884598f3f91bb8195c83ee4b322c5a7c9c2fd7', '6d1d009bb22acb63', NULL, 1788293528960, 1790885528960, NULL, NULL),
(140, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '78342734681b01c587fdd4cd3500b83d81f80f3bb0abc735af4aaea0306b8679', '6d1d009bb22acb63', NULL, 1788293530349, 1790885530349, NULL, NULL),
(141, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ea8f74b696f5abf54b19788e3ef26aa1735aa1f19e906e81be6d3e05afb52af3', '6d1d009bb22acb63', NULL, 1788293531466, 1790885531466, NULL, NULL),
(142, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '569887edd5854be5f148a1047d59b9c1185003c5e927cd2b3eed4a2603937266', '6d1d009bb22acb63', NULL, 1788293540901, 1790885540901, NULL, NULL),
(143, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a797ed09636cbceceb522020d94ad4125580e316632f9ff6a319d167b43b23e2', '6d1d009bb22acb63', NULL, 1788293581772, 1790885581772, NULL, NULL),
(144, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dbdc3abf1d3330acce4aaa5cd14ffd14c26a837a9c8a265f0545111d456e0706', '6d1d009bb22acb63', NULL, 1788293590700, 1790885590700, NULL, NULL),
(145, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0714ef1343a5e7c18f6e7f586e40348bca25b73e5dd7746f7dd020b3a0bb114c', '6d1d009bb22acb63', NULL, 1788293718735, 1790885718735, NULL, NULL),
(146, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dd9bb2446c0a10b9c9b079717f6563c436dd8560a28804df2a50517271a1ff5f', '6d1d009bb22acb63', NULL, 1788293770356, 1790885770356, NULL, NULL),
(147, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8477439c65ed4995bc4c45d073c00b85ace1c819b4a5f55fe6a1319862706fc9', '6d1d009bb22acb63', NULL, 1788293771523, 1790885771523, NULL, NULL),
(148, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '333ccd5942f7b54cebbc56ced4d2b4d215da3369b7912a49e29142ccfd339240', '6d1d009bb22acb63', NULL, 1788293772497, 1790885772497, NULL, NULL),
(149, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '532d555a61f7d4d8c9794c96e450e2b352ac66682939ad7d3cbb320d7f6c008b', '6d1d009bb22acb63', NULL, 1788293773318, 1790885773318, NULL, NULL),
(150, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dc4ff21e55e673b7231aee941f0cf3ea3903fa476925e817da8425e623e29b1a', '6d1d009bb22acb63', NULL, 1788293774232, 1790885774232, NULL, NULL),
(151, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1386e88e76385ab742898e1cab54bd1bb4c6be1c1cb89ed762ffa58810ffbadb', '6d1d009bb22acb63', NULL, 1788293783905, 1790885783905, NULL, NULL),
(152, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1cc273e64f94b40ed9d2a64bd5f11ace1f1395ac6a8fa6ceb346095657c9ffb2', '6d1d009bb22acb63', NULL, 1788293790983, 1790885790983, NULL, NULL),
(153, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '18007bf5c8a05352aca73fbae94eefb26c0772b625fdb66fb8af1047d437cf5b', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788293875317, 1790885875317, NULL, 1788296550310),
(154, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ca0796a63da6d83a0f7a2703771b5dab18239e1921dd3c7732c92d064c79860d', NULL, NULL, 1788294983035, 1790886983035, NULL, NULL),
(155, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '797c3e60c0b562f3be9fcdaf4cdc8245aa048c00e968174fceefe4676589c613', '6d1d009bb22acb63', NULL, 1788296354320, 1790888354320, NULL, NULL),
(156, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9f2612d8b3f4bc7fa876a35e42c622da26e794f943211d6dbf209f9be1612975', '6d1d009bb22acb63', NULL, 1788296355913, 1790888355913, NULL, NULL),
(157, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6905828a04df8a3f00d91eff6bba941b90631f484e1c28ca6e9f7ec1c5c74005', '6d1d009bb22acb63', NULL, 1788296417833, 1790888417833, NULL, 1788440736896),
(158, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c075915ea16b59f7c6f1d5fcc831997652449df817db8c407a4b4b6283c5aba', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788296563213, 1790888563213, NULL, 1788298699584),
(159, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '319eb95b6cd022ecd51e16746259ef13cfdb598d8d7301271d89ed89cdba4908', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788298722939, 1790890722939, NULL, 1788302750017),
(160, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '96ee74e810e34b2cf34a5332538ec2e5a29c54263c3b77d7f092b5c7f25906af', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788302758030, 1790894758030, NULL, 1788304256429),
(161, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '05dc7653bfa03a9aec9ed0fa6413f3f713ca4d106e9fec7ef23ed058c29d357e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788304266372, 1790896266372, NULL, 1788304348790),
(162, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd7aea39ba58f068f8ae1cef0b3679437f4f528272b9ce8b6c5a9be1f3450fb18', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788304367850, 1790896367850, NULL, 1788304777222),
(163, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4b6aec9d9784ef25e019d3833f9ffb72f30728d6c96dc3e91148027cc798ffc9', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788348571963, 1790940571963, NULL, 1788351485379),
(164, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '26da172999d6b780c5085e91f394cdb539e715c79bf4f11ff0158834179d4e24', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788435195588, 1791027195588, NULL, 1788436638648),
(165, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'aa6099ccf93923cbfa85917b169bfc53ec63205c14a00380d4f833a3670f149f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788436648864, 1791028648864, NULL, 1788443018327),
(166, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '442fa0392bc56a727fd4c348a20ecec3f6a97a389c3801efc81921bdd71b7d1d', '6d1d009bb22acb63', NULL, 1788442891569, 1791034891569, NULL, 1788443982894),
(167, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c30e12c00a605f2a7b036eafe24a7f90bd5668720313042f6a51bd80fd22a39e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788443028898, 1791035028898, NULL, 1788443755823),
(168, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a1a9df07f7542b68ef45b50df178bd7f90aba7d7e2e627d168e99d1ea772d3fc', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788443764122, 1791035764122, NULL, 1788443921848),
(169, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5416c94701456f976a83d36f77ce553822e156c46c635b173760a0fde0ee3dbd', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788443928962, 1791035928962, NULL, 1788445877012),
(170, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0898d9bb25bed9e8c404ea8a238ae045edf159a92020f17a7d2de15b3c0797c1', '6d1d009bb22acb63', NULL, 1788444074583, 1791036074583, NULL, 1788558810372),
(171, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6c8ccebc254d77d382b976cca10ad6131d7a7ccf11b197b911fe16e6c5d38783', '410b6712-d889-424c-9bba-ae7929c24908', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788445933400, 1791037933400, NULL, 1788448141720),
(172, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0b14200278df32ccee57b27dc7b933270d7645cfd0a2f7adb52973140a8637db', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788445974242, 1791037974242, NULL, 1788446252493),
(173, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '42e23c526b3fc592d08eef3a8cb259296620c757201d8af37105ce3a1c0a5feb', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788446259222, 1791038259222, NULL, 1788446399742),
(174, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c01fa2fcc2c0a89fbf3a22695b60e61629e059b85ed362e89e6b9b73ced51ff8', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788446406363, 1791038406363, NULL, 1788446407016),
(175, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '287023a5a45d102c0ce8d0bb944be048712cce43f30fa8d0681ff8efc17f9dfd', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788446418660, 1791038418660, NULL, 1788446988749),
(176, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cc54bd4a3187c25dc7a8fe359872fe0ef459b63485c33d10f63f8a8fa89a5357', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788446994719, 1791038994719, NULL, 1788447958088),
(177, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f9b9bd9229b6121d9e9ba132612a47879a6a1394a9b39c3582f0fc06c1e16eda', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788447964934, 1791039964934, NULL, 1788447999781),
(178, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '98657ac2431f6e961d470e6db854b315707874308d694cefec9219e585cb3405', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788448006836, 1791040006836, NULL, 1788448172429),
(179, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '99aab9c0e5fa06c43dfb2819b160cecbf1b31548e2afa54b4612e321c42f9261', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788448186695, 1791040186695, NULL, 1788448227679),
(180, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ab89ca7663d05938eaaa0d3d91d78c8e1d37cbaed2f15082d87c9cc80d2b5a9c', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788468338565, 1791060338565, NULL, 1788469027330),
(181, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '09d37ebf2433b4301c285a91f035da4636b053d6c7003a9e6fcc67872c478ea5', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788528122251, 1791120122251, NULL, 1788534943663);
INSERT INTO `auth_tokens` (`id`, `user_id`, `token_hash`, `device_id`, `device_label`, `created_at`, `expires_at`, `revoked_at`, `last_used_at`) VALUES
(182, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6721141cef143137c488473b300115941466ae0f66dca4eca7e75fb72f91511e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788534966785, 1791126966785, NULL, 1788554514269),
(183, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e3d1ad65dc0d7ad6269064d3df7b557099750caad0dd30c3a8d72727f6e744f6', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788554522287, 1791146522287, NULL, 1788558531320),
(184, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '75020ebabafd4d25cb5af0adc055a76f896081dc9ffc1b3189900e00dc26b099', '6d1d009bb22acb63', NULL, 1788558865126, 1791150865126, NULL, 1788558874057),
(185, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e3ef1ac1fda4bf01c5947e79ee1c95c79729aab76a2170bbf2a5d50e3c884a23', '6d1d009bb22acb63', NULL, 1788558919023, 1791150919023, NULL, 1788774332187),
(186, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6362c42145b94ccad0935c97002875a1a5ab76a2839a2ef374b250502365faa7', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788558968245, 1791150968245, NULL, 1788705202800),
(187, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'afeedc6aa40c786bd1a9007dbb31c23b264eed0303f0db2a81c4ec7cc7088bb3', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788695637002, 1791287637002, NULL, 1788695692947),
(188, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5c840e33d233e5a064f804f8d1933d970e1ffb4eb0c63c6cf8f4890bf4a7fcfc', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788695711771, 1791287711771, NULL, 1788696079053),
(189, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a6cbb11898fa29ce7b6ce7f1f012604dabfe4f080a0306b00c127ccc0f9dd360', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788696099470, 1791288099470, NULL, 1788696448967),
(190, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f7348243ed73c90966f5a8102022ddb03bdfdebd608837a43eaafb7983374798', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788696457173, 1791288457173, NULL, 1788696458606),
(191, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b9d80579fe8d7cd76e57cc8456dd23ade954bf09736d33904a7d090e51528eaa', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788696473211, 1791288473211, NULL, 1788698664096),
(192, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b4219349d865cc37cd414b452cdea4afcab99f3c766ef3b988a613b42bf85fbc', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788698916893, 1791290916893, NULL, 1788700071537),
(193, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c2f3c20270f772c60845c887727cb0e4a119163cbe32db31ef08a5bd1ef2d5b8', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788700081255, 1791292081255, NULL, 1788700240700),
(194, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '958c54f1edf43f6abc23e0ca3595d8db301ba3ffc0666a496b513589542d9677', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788700250296, 1791292250296, NULL, 1788700400648),
(195, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd2b24ae2057f94049a8f76660ab82c628ad94ce920dad2b72ff1fcb2ceda07c4', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788700420798, 1791292420798, NULL, 1788700515847),
(196, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '37482431812d8e8ddd31bd6d72154bbbdb9c427f259e88a3dfc8ec06c37d1833', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788700523139, 1791292523139, NULL, 1788700872416),
(197, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2654386af2307f55b3f993b58a199964ad4a0c8bd67a5aeaa0689807d36cbb1f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788700878978, 1791292878978, NULL, 1788700904464),
(198, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5d0b1f767f110a9d6d217ea1fca8b67ec844313ca6342a911b373c310bf7276d', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788700910595, 1791292910595, NULL, 1788702074595),
(199, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7587b6b76131f1090c37f504955c5414bdb577c4d9b3bb37217c60154721e3ce', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788702094096, 1791294094096, NULL, 1788702097635),
(200, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0be63c9d56d61c95b706cd739606c182f8fe416274b4980a7829d83163ee2346', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788702117277, 1791294117277, NULL, 1788702168603),
(201, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '204b9620803c0b8a6bc6f553b24a2d0d1ff78fb5362849d695837ef4f67347ae', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788702174620, 1791294174620, NULL, 1788703490571),
(202, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8cb24c525dbd8a26b96c7477aaa20ef4486cb1b80b98dffa63848c03daeb6219', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788702628800, 1791294628800, NULL, 1788702881396),
(203, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6c44bcec24f2bb85962a2f4526e4469eee0fc52de786a318ac7cb8b6b51a019f', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788702892655, 1791294892655, NULL, 1788703054144),
(204, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8f69604e4022f598fb8b0af3e3e019df3523e4feede36e0cd44b418e10615db9', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788703076657, 1791295076657, NULL, 1788703356933),
(205, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'de67d132896f42101b7641bf07cac8ae1af9cbc6dba33ff8d92ede2fc7524948', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788703377520, 1791295377520, NULL, 1788703603722),
(206, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '87558aac8e423c356e9b9a8cdc3fe5c7f0dfe67a6162f19615b51be9d780778f', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788703632371, 1791295632371, NULL, 1788703976979),
(207, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a7c136cfa739292acbd11f553579fcae8060d1999bc1bd8d4f325370992c4d8c', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788703991339, 1791295991339, NULL, 1788704236161),
(208, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1b1f16b07025a1284599ae19a1ff493fa87a2ee84df777982946405be2773ea1', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788704250174, 1791296250174, NULL, 1788704565347),
(209, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '176a3d9ff00a274bfdad693ea3cbcb4531f8a4bf7a82fd9491cdf11dc7394863', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788704583537, 1791296583537, NULL, 1788705220516),
(210, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '40f515dadef29e076ad636038d1149a30add422328a4456067c4f4bce9a8111f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788705211835, 1791297211835, NULL, 1788705628483),
(211, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6dbc1ca70b8a1a6d5722e847e6585a961ed278f56fe11606a300fd658294f9cc', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788705243155, 1791297243155, NULL, 1788774544945),
(212, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '529b7632a171eb265d3095aa60de2f1ea2c59726aea41a1005f58c1a18bf425d', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788705637580, 1791297637580, NULL, 1788727750210),
(213, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5b13442f03b4ea4db66838e37750f143d8bdf05909b778605a963ed63785fb1d', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788727759734, 1791319759734, NULL, 1788729735582),
(214, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1cd95fe4d7907436df385d95b0d9ecccb99a4e4768459707fe2b37a8fde946bd', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788729749298, 1791321749298, NULL, 1788730538475),
(215, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ef1e3b4a7d805d054613358785115720ebab2742c1c4a4bbde7ab88e26b5101f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788730552975, 1791322552975, NULL, 1788771359873),
(216, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b3ea1639a2bdc2b7d8b97cc30a12fef6e06a4d92f89c79a7b70ed356bb8c5b63', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788771368026, 1791363368026, NULL, 1788772608842),
(217, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1b667862fa265882813ffca6759c1330933c2bc88110cae126b6cf5b3ac62336', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788772615890, 1791364615890, NULL, 1788772621578),
(218, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8d9ca6364ee4aa0d5471e218d33a3f07b392a5923599d592ea17d5b6a1d1b036', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788772985193, 1791364985193, NULL, 1788774555193),
(219, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'be8870c38ebe9887758ef6de15367c058c09727ad383dc80f648d4016d8a3311', '6d1d009bb22acb63', NULL, 1788775008311, 1791367008311, NULL, 1788789493759),
(220, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f7ae283525bc179bced2605bb76049560e730beec399a672a1338eb5133bc0e8', '6d1d009bb22acb63', NULL, 1788790874131, 1791382874131, NULL, 1788791874594),
(221, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4f22b62cb6fabd8f87b44402ba597eec991a23267505ce430468c194a83244bb', '6d1d009bb22acb63', NULL, 1788791912871, 1791383912871, NULL, 1788791913506),
(222, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '79fcde8e210f1b2169a3ca395f60da2c01d954488a8c08f332b29b57efb27329', '6d1d009bb22acb63', NULL, 1788791939042, 1791383939042, NULL, 1788876247251),
(223, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8213a7c4e21187543e3ab3e98b01f612d3af5ea99e81dae98139eb0ffce4e2ce', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788800110103, 1791392110103, NULL, 1788810283013),
(224, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b15b36b5f553b3b10241ff2f4e55d27e44a0b88838d7c8d468f3538fb077b76', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788859925338, 1791451925338, NULL, 1788873518763),
(225, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '03ed225aa82be4e42e7f9be4d4d2bf8befa86504741f33e674bfa069ff7fdbed', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788873531172, 1791465531172, NULL, 1788874064554),
(226, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c36aefa537aea2504f266ce74ce1df63ae9fe2b18c198b04be0e95531b91aa4d', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788908885166, 1791500885166, NULL, 1788908894624),
(227, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9f7bf7df5f7322b1991dc6c6ab176f20810e4512c65753d19733a26e8dea3f1b', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788908910428, 1791500910428, NULL, 1788908912996),
(228, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '57ece04b9b46570ec5cb2b5ed3e17b593ab88e7848686cab1ab821e066ebdc64', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788908925287, 1791500925287, NULL, 1788909122939),
(229, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6175fbee706bff293953704a0f8a46915b7ed978a5766cacfd11ca109afb8578', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788909214669, 1791501214669, NULL, 1788909783246),
(230, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1eabe3512d8466d1169f1063ff2a41a241ecc1271b769461d625ce96b8b5f221', '6d1d009bb22acb63', NULL, 1788909392595, 1791501392595, NULL, 1788909397172),
(231, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'eb83309738374a7179a789eb94f88fbb9fef712ec2cb6378bcc3846d270ba550', '6d1d009bb22acb63', NULL, 1788909757854, 1791501757854, NULL, 1788909964624),
(232, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dea21653fc063f6c6b81b935d42407c19cf193505be70b66dcc71479fea6e008', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788909807850, 1791501807850, NULL, 1788909828095),
(233, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2f21c8aa94a1ea0229d66ba295e2da897711557c93d0f8c21c00dce0f482b362', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788909842749, 1791501842749, NULL, 1788934142362),
(234, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '00e219252b6869c6c0e5ed84a3e0271e1a90ec581927e88ce87b4720a798563b', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788909914529, 1791501914529, NULL, 1788934193411),
(235, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8ec37bb572a1a89cd03bc4ff757d7d5b1deaf84fdc4a38028d2630eb8adff5a9', '6d1d009bb22acb63', NULL, 1788910008551, 1791502008551, NULL, 1788959296427),
(236, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '66c45995f2d4f2942fe0ba39135c7dd2456ae47f88f12972d8206fe5a4ea7deb', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788934158169, 1791526158169, NULL, 1788934174808),
(237, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '52b43c7cefe8edd50e3085676af75da20144198b54cc13bee2d32cce80b03bdd', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788934185708, 1791526185708, NULL, 1788935208477),
(238, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '02d650ab604eda1c2978875c5000db0d9a1e260b7e9c830e3d9b43ecae75f2b3', 'bcd5ac24-3833-446f-80b2-870640e073e5', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1788934225109, 1791526225109, NULL, 1788961327862),
(239, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '50db467a8c80c708b4bfee831c58d91757eb83f55922152ac84409be16f092e7', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788935272388, 1791527272388, NULL, 1788935300187),
(240, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3f2b7be6491d8111de4610b5ebe2bb8ecceb7c21fb1f396595104d2ec076b0c9', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788935316845, 1791527316845, NULL, 1788943162934),
(241, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '030df539660322e6a46acc7e9fd1a36f1f4574a7045d0deb55f943693ce750de', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788943171428, 1791535171428, NULL, 1788945580548),
(242, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6c7af4fb158c14597fdd997eaa6c9a199458d9db962246aaca1f5044ca09f790', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788945590476, 1791537590476, NULL, 1788946125084),
(243, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cc6caa81bde8e566c8552ed021790a5c0cab4b4541d11ddb1c1881a13a26909f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788946132507, 1791538132507, NULL, 1788947031900),
(244, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '08206960b87f1be2fd60b16e914148e73ae1199225e929de82851e8cd39dba52', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788947040829, 1791539040829, NULL, 1788959179597),
(245, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4fa380aa45ebbc71d714b5d9f4edb994ac8213678238be4d147be53e2104bd7e', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788959202990, 1791551202990, NULL, 1788959286776),
(246, '911d1263-1149-4d4c-8570-1b6644a6d9a5', '073d148652e34fde5f0c59b9adb4149b26844f20626da0206dd59f91910bc8a7', '6d1d009bb22acb63', NULL, 1788971271334, 1791563271334, NULL, NULL),
(247, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8860b598e98759535fe9ea5fdca95e262287753c2790df024cdbba60d3bb0abc', '6d1d009bb22acb63', NULL, 1788973693587, 1791565693587, NULL, 1789031919594),
(248, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bb90f89e88446ed72fd810c3f35e16e26af54ec20829ef3ef56654bdc34029b7', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788978899682, 1791570899682, NULL, 1788978920494),
(249, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ada3a7d7039c21daa1c65f3d22f7c2bd2c34e11f30084bf90411f88581c9cafd', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788979254800, 1791571254800, NULL, 1788979474650),
(250, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ea9ddba5417d1d5ed05e3549b7fda5548809a2501309ed72c94e313350a0d6ae', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788979771259, 1791571771259, NULL, 1788979997136),
(251, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '90a5a9c1b70b76adc4244e2a28292f109f684aad3d5e91a3c3f0ae982a1da59f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788980065945, 1791572065945, NULL, 1788980067234),
(252, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9f9b00777b5b990e95e41100580c83056a68cce62f248211c8f20fed9fccb817', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788980238840, 1791572238840, NULL, 1788980324295),
(253, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c72fb6d149f26296cd843594b40e9bd59a8f9e1f3448dd3ee59fc4ce44c48dd8', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788980379501, 1791572379501, NULL, 1788980380240),
(254, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0071d019782d1a57283fedb39efe26244eb82e426e39c09c1876f306505e6386', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788980716041, 1791572716041, NULL, 1788980838064),
(255, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd65371cdd6fbca0a7358484a3f8f65fc04daa3fe50c0bbf404a4ed8f9f3d8393', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788980881401, 1791572881401, NULL, 1788980903777),
(256, '911d1263-1149-4d4c-8570-1b6644a6d9a5', '427db0845d3ecb7e8b4e3d2b1765e3f1cb44b5529675422c34966b36ae1b8672', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788980933221, 1791572933221, NULL, 1788980948709),
(257, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '23a0a79a75d97c93fde4fcd3766c787bc699733500e5c20ce9a6a7b94875837a', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788981004119, 1791573004119, NULL, 1788989039548),
(258, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '44919420948db50618cd9170fcaac9a87f9e0239aa081edbfa1e65909d5408f1', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1788989051802, 1791581051802, NULL, 1788990747223),
(259, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd5fc792f9c43e6c7ece29d50a375dab928ae7724480510ec9c10ae5b10839354', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789029574516, 1791621574516, NULL, 1789032040477),
(260, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ee34d87b7212adcd290fca5350f7334b2799f107412a859176c8127901695009', '6d1d009bb22acb63', NULL, 1789031998861, 1791623998861, NULL, 1789035193764),
(261, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '92cf85f8015bd9df2d0e0b451fa0a23fe44fb12eb56ee86757b720051d4983b5', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789032047798, 1791624047798, NULL, 1789050971947),
(262, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c787916070c2691f80156b0c624a305db0752d06833822ddd650cbd6d3e67651', '6d1d009bb22acb63', NULL, 1789035770393, 1791627770393, NULL, 1789406111534),
(263, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9a7791276193a07b3b866e1844ea1c070052a9b0db4451550c0fab48f270d36f', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789121986802, 1791713986802, NULL, 1789123134745),
(264, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e19309f89b22954279bf8c0172e1d61db57b98981d3029b296a80014a1e1da8a', '041a7702-2d66-47a9-875c-6006f5564962', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 1789122068695, 1791714068695, NULL, 1789122079680),
(265, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '668846258ddfaa073f0d9e83ef8c4809e1983f4b9a20b4fa7f95548d03b0b881', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789134233190, 1791726233190, NULL, 1789135189518),
(266, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '10b65c32d337cc4ce4fe03fcea27436b8c3e7be4641ff456b1daf289e2ed17ee', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789135220353, 1791727220353, NULL, 1789161500084),
(267, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1bc4334e60f7053c504f3ddc678a2d52870c0a1757fa995a1329c15fdcea0c04', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789388324631, 1791980324631, NULL, 1789397005248),
(268, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fb3998d22a2bd70f7ca37ffd76b41e23dfd1832c178e562b8b3470e64bc38a31', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789402072416, 1791994072416, NULL, 1789404707098),
(269, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '285bf90b01e75d76484c8a35e83fd6d1de4eb54501a3b27e1fe8702405c5b682', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789404721205, 1791996721205, NULL, 1789405611693),
(270, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2fca2fe7428d588e50dbd9d5e2b473dbba17656fab922387131958fe7dd4ab32', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789405943129, 1791997943129, NULL, 1789407076516),
(271, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '665539cfe8e8d078ba4aa419a13ea606cb6ed2105f52e7fae0f657d1ef6bf4cc', '6d1d009bb22acb63', NULL, 1789406290144, 1791998290144, NULL, 1789492073600),
(272, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c6b14b07a3b06d2231bec68bedb3a232d670f7cc652b0049e2c5a458febb3703', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789407313403, 1791999313403, NULL, 1789465982378),
(273, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c9c317e50a79b3c07b3dc1726bf226c239dbe2361e9809e4cc3eca73ff721489', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789471738762, 1792063738762, NULL, 1789472953702),
(274, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c998baa5338505eb2e7804871d1b3690df9f22b41714d70cbf845301a662a767', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789480273938, 1792072273938, NULL, 1789482392548),
(275, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3489844ba3d8d5599dc441036115bd7cd2f021bbd8d0e1940560d411433adcf1', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789482466774, 1792074466774, NULL, 1789511978981),
(276, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c66aa41bb60e11831c890992bf2e01d2f473d354880990a296f6db06c119cca2', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789646446377, 1792238446377, NULL, 1789646491419),
(277, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c96b09d576c51c495636970ccfa0b65f58c6b2e28dd61e9957c998ab59f41640', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789683942034, 1792275942034, NULL, 1789684064089),
(278, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e116be192e955e21fba0bb195e58606c3cd687a237e9af66570d041e0b39f223', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789684310909, 1792276310909, NULL, 1789684432360),
(279, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fc497a373b2f7b488744f1d4647692e6e4499da71ec47ec65c922a523f7db226', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789684440940, 1792276440940, NULL, 1789684497836),
(280, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b7832a67d8ba1c1562abffed171f6671a8f38d4cab0696224db6a8fb13bf00a1', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789684506771, 1792276506771, NULL, 1789685074549),
(281, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd306c8b005af4e291c262a6191e6f6f1a26f7348980d33848961bc130c7bf13d', 'postman-test-device', 'Postman', 1789685033898, 1792277033898, NULL, 1789685050256),
(282, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8f9de68b9c2ef04384ce9d5d5e2d31c1aa974f0a2f85caf0cb18212dbaeee2b9', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789685086360, 1792277086360, NULL, 1789721013440),
(283, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6c478cfa5acc5b0585f7ec37a0833edf1e581eaf7343774704ae680eca869f27', '6d1d009bb22acb63', NULL, 1789685211496, 1792277211496, NULL, 1789685319202),
(284, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '22176c8366509c1d27f296aadd836a62c94698d3ab91054d270b08a896a978af', '6d1d009bb22acb63', NULL, 1789685442827, 1792277442827, NULL, 1789719814153),
(285, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f7226da48019217d0cb91563316322b5472022658850c40b54edd897baaf7824', '6d1d009bb22acb63', NULL, 1789720041107, 1792312041107, NULL, 1789913613387),
(286, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '22b319f8e100c0cb901601e3f15475d2b6636540e029ed358fdd9e6251be8257', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789721020221, 1792313020221, NULL, 1789721647786),
(287, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2b2b1670e154edd3d267ac932c03d8e18f496c1c2671c7c18030d6a5fee92491', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789721736720, 1792313736720, NULL, 1789722484309),
(288, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fd757bb3b1279f1e0bcef2ddb9220fbd1c75f75d4530c63bed904c15c59473fa', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789722502466, 1792314502466, NULL, 1789725190980),
(289, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'becc1844757a0312f8f6739195898616378988164474a5f097672c947bf32853', 'f66cb33b-4301-433e-9aad-15efb47cb257', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.5.2 Safari/605.1.15', 1789725188916, 1792317188916, NULL, 1789731462065),
(290, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f84439555deac47887d4f8403b2fcc5f16a45138da2d353b2a784b5e2f6d57a9', 'b0323cde-c481-4a26-afff-8c3e44d02be7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15', 1789746187796, 1792338187796, NULL, 1789747704131),
(291, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6be28ca58ee3daafc95ad461a6e91e7888f18f4462896d3281e64798e6a2f445', 'b0323cde-c481-4a26-afff-8c3e44d02be7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15', 1789747716105, 1792339716105, NULL, 1789762521507),
(292, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c0105b78620283abeddcae73754953ed3d54094a21045aefe8cadee4b3c033a5', '4c4aa4f5-a90e-42f1-8f3c-0e83936d9d10', 'Arziki — macOS', 1789751785814, 1792343785814, NULL, 1789751810144),
(293, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f96fdccf127631e282fba52eca163d224f8a9bd2961c8012273c094bf8820aa3', 'e8a18f9d-55ff-493e-970c-f4789b936aa0', 'Navigateur — Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36', 1789845147153, 1792437147153, NULL, 1789845694904),
(294, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6bc33e6eebb71c49a2f2d583ae06a685d890cb1355f2b6dbaca1c4371b249d6f', '4c4aa4f5-a90e-42f1-8f3c-0e83936d9d10', 'Arziki — macOS', 1789897315284, 1792489315284, NULL, 1789897326557),
(295, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '27b7a1534ca0b110bc23e5e5ee81c8cd17bcc41580e5e4acbbfbcbaa25a9b9ff', 'b0323cde-c481-4a26-afff-8c3e44d02be7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15', 1789905536259, 1792497536259, NULL, 1789933625599),
(296, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cae971fb3d1255ec0afb238662fadee3495d818b3c4cc32888c8c4d3d2d0e4dd', 'b0323cde-c481-4a26-afff-8c3e44d02be7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15', 1789910421828, 1792502421828, NULL, 1789910486839),
(297, '87704bcb-2b48-4f9a-a2ce-128b2cba698e', '5df21c1b60d0026f909325cf4d3381408051808269c01232edfc6552a73fcc95', '33aad3cb491ff620', NULL, 1789916128616, 1792508128616, NULL, NULL),
(298, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'b4bd209bec55ed2402e5d65d124128d6c777a659b7c4ada16d523649e8cf42b0', '1e7f044baf425b82', NULL, 1789916308625, 1792508308625, NULL, 1789933586607),
(299, '548eba33-9bdf-4f1f-be06-60e2220b07c2', 'a883c54b6348576b7164d81810e0fe0db005cfcde6ff2dd6e7ab682837cf01a0', '33aad3cb491ff620', NULL, 1789922738007, 1792514738007, NULL, NULL),
(300, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fd009c80ae31a6bd611a0600542de575ba9170d8085a98307b73b6c6957a5b1a', 'b0323cde-c481-4a26-afff-8c3e44d02be7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15', 1789933681595, 1792525681595, NULL, 1789933809636),
(301, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3c03421ab8ec57a8960545ff358f4511eaaff1329b927ab60dec2ed8e635789e', 'b0323cde-c481-4a26-afff-8c3e44d02be7', 'Navigateur — Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15', 1789933825992, 1792525825992, NULL, 1789934556451),
(302, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f84a33248adc9d3ac4fabb40266c257f49ed5c0d0c0028bc0b2e309f7c960979', '1e7f044baf425b82', NULL, 1789934464466, 1792526464466, NULL, 1789934522158),
(303, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c2729be900021d9cefc28691e4578fab96a78ea9a3cd73ee07c8124e9ab7bfcd', '1e7f044baf425b82', NULL, 1789934536964, 1792526536964, NULL, 1789934614312),
(304, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cad3e1779956fa1acaaba7745acddd831ddc8829de6d5da50f415394c344fadc', '6d1d009bb22acb63', NULL, 1790065554114, 1792657554114, NULL, 1790076630541),
(305, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '849cb1a06d1088aaca73f79a80cde7eddc86c59ab7f54740d1a5c0a463c6e215', '6fe6577503d2ecec', NULL, 1790091838462, 1792683838462, NULL, 1790157721040),
(306, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e64c6395365379ee61d89b03e97484dc801ed4b12a5d41ead32cbd0a95768246', '6fe6577503d2ecec', NULL, 1790157258178, 1792749258178, NULL, 1790157275250),
(307, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '00248935a38af724867d8da302fc8ad57d4bec56249ff347a28840b6551aa6eb', '6fe6577503d2ecec', NULL, 1790157925021, 1792749925021, NULL, 1790257453533),
(308, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4dba5d41601ac9ea718334253cd6ce143e6dd77ae00c3648db814695d85d51ae', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36', 1790183767808, 1792775767808, NULL, 1790183788615),
(309, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd72c270ee5bd5b9617ebaaaeab598ab24f9e56c82b06e36671a8b99deafb9d56', 'ececaa62-4d9a-4eaa-9244-c32249623bb4', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36 Edg/153.0.0.0', 1790183927772, 1792775927772, NULL, 1790183953146),
(310, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '22b14c035fd9eda213e2beb2f45ff5d34f177862ce278b8a9d1edbe65b857c22', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36', 1790191778491, 1792783778491, NULL, 1790197863654),
(311, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0a47c6801f69c4833fd0f68692e11e64925fdb250ec4edb4676b31bd4e234a9c', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36', 1790240973878, 1792832973878, NULL, 1790260818702),
(312, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'aaa1653e5f844f214fd21c07ba0dc5e9a7d094651298b23955a2a237b64bb305', '6fe6577503d2ecec', NULL, 1790257627789, 1792849627789, NULL, 1790257632556),
(313, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '44a01131ec221ab62d03d79277c9b1425d2114f1a973c90adcf5b02502788a7a', '1e7f044baf425b82', NULL, 1790260726421, 1792852726421, NULL, 1790282360568),
(314, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1f2fd2abcec365126fe02fc06b39cd4b18a350be7cde5b6e86af878fc00e15b2', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36', 1790260834557, 1792852834557, NULL, 1790291426055),
(315, 'ca06ac92-8ed0-4e21-b5d9-9693445a5a7e', '0f816b619194b9e966e09d7977a0302361ad0945cba383b45a4d8a48c370d7e3', '69eb249e5158a332', NULL, 1790279278673, 1792871278673, NULL, 1791207603757),
(316, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6a5271ff97e2ca890e34ed828dc100403d114771ce812603ba89f2440945609e', '6fe6577503d2ecec', NULL, 1790288524972, 1792880524972, NULL, 1790367473416),
(317, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e1c382d4b9375ed6e24082d987509030d2d3167f8aa75a6e16d956526277bd3e', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790328187364, 1792920187364, NULL, 1790328692832),
(318, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd4717a090c0d0ecfae319001f0115e5da51391df8ff84525dfa8d78fb5d87fa8', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790328698517, 1792920698517, NULL, 1790328785637),
(319, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3e037a708952f016d24242cc533dacdbf09d001b1c14282304dd4651de16cf77', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790328797004, 1792920797004, NULL, 1790329242835),
(320, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0e1ceaf4ef4df394a96b279f4b491ea618e1f577d98b0137d9e7675f6bb18fba', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790329249382, 1792921249382, NULL, 1790333914703),
(321, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1e738f59f69c9abda88fa9b7ae2458d30203ddabd22a6e81c4029757d2d4f4ac', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790333994926, 1792925994926, NULL, 1790334891739),
(322, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5d9fafda1e5d6625bcaa7f2fd061340c458f7eb4ae25fa66ce0c3434399ff3da', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790334896646, 1792926896646, NULL, 1790334977206),
(323, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bcb98528f5536ff08b7d7872d797aa96aafd81d25632289d3e2fb028911ac64d', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790334981948, 1792926981948, NULL, 1790335026914),
(324, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '490f9d1ca23b47fee965818fe8e5775596f3b7a245ea2eaa83bfaca6e320e641', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790335031794, 1792927031794, NULL, 1790335166188),
(325, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6951e4050e07196dd2334a6c2d9b1e0a7c322a30f8aff055c0af1d1b6b947dc5', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790335171600, 1792927171600, NULL, 1790335292306),
(326, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8520b2e31ddf0d8bee4bff20d24732854a1adac35b8e5de4e000665098f32bd6', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790335299477, 1792927299477, NULL, 1790335360107),
(327, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c0d577dc524b93e74d5de5c7c8658b0c3a412c33b39b673570fbfdbde2e69af', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790335365215, 1792927365215, NULL, 1790335579113),
(328, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '49b65b6659fbe0193bfd73a8c3013d18c7d6c4751c6f641efd364cd52378ea62', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790335584167, 1792927584167, NULL, 1790335878054),
(329, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '82ec4ddbff740033b9b8d2f9f350feb84f9bd666db3895ac2b9f4b0ba3a596fb', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790335884616, 1792927884616, NULL, 1790336580186),
(330, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0ea7dd4225bf3eb411165ab37bc00ac7ca9898207f2bda2f09aba6eafb2c3ada', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790336598245, 1792928598245, NULL, 1790336648206),
(331, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '22c8e57e770566f7d651b7d1531c8c02713be5248cf6c75340d51bee103d9a6d', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790336653471, 1792928653471, NULL, 1790336710032),
(332, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e6c71b101bac5d5c4094638f37100b57678ca04a3797dc0a0660fc9d4becfd5a', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790336715777, 1792928715777, NULL, 1790337403076),
(333, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '12fcda96ed93f60b7a064275b797bb01f6ee3bbe6ab9a2acf1e9cebc0359d8be', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790337413431, 1792929413431, NULL, 1790343488166),
(334, '544458f0-b7c9-48f6-9559-c189c6fcc3f5', 'd6ad24ed53042a7c19b062390544a6c68123ffce3294af978024e0c156ce68ce', 'b0c53d13050e9ac5', NULL, 1790343018385, 1792935018385, NULL, 1791202163329),
(335, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'd6959bb58103ae871df44def270bb6983048fceb1ac6e8c63f839fc52f19a740', '741ab383e1ab2cea', NULL, 1790346287109, 1792938287109, NULL, 1791202420693),
(336, '5e1c8db5-c66b-48bc-8b53-d43a1fc8e701', 'd9794829b36ed9a3da0eeff3f4696539a3b977dac093987df04f539914c0db8e', 'f7abedcfba5f17d9', NULL, 1790346614013, 1792938614013, NULL, 1791168704359),
(337, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '3179ba5eaa326a31b3745757576478c67a430e254957a8ea934d7b6154c056ba', '333e0500a35884b0', NULL, 1790350293659, 1792942293659, NULL, 1791165197982),
(338, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '8e17f2bc06460ad0f04d381b33616445b51992c4cdaec4182a32e5b6593c862a', '9720c9d6d8f1bc39', NULL, 1790351875302, 1792943875302, NULL, 1791197928761),
(339, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '3fcf85b2a27959b4fa12d319df589e52e02561da4830c9d66d6179d8c65f86bf', '590fa4eb5c45ed91', NULL, 1790352418089, 1792944418089, NULL, 1790492913602),
(340, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '876ec5cb222072639b2e74076854093a834484a11d1bc9f43c7abfb66fec4caf', 'f22dbc66fa6a7146', NULL, 1790353593357, 1792945593357, NULL, 1791210458460),
(341, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '848a9798dfe6eddac1216179d459b78d1704630ccbc4d9c3123710dd0f14458f', '001e2fb8cad0c6bf', NULL, 1790354401946, 1792946401946, NULL, 1790354419217),
(342, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '70472564daa9de8153541fa8bdd7221a7867fcbbe48880ae408986e9f98dc04e', '001e2fb8cad0c6bf', NULL, 1790354560313, 1792946560313, NULL, 1790354564014),
(343, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '643dbf1ebde0df150e40a55f2f01dc9516c4176ff8155aaff2a68f84c42add8f', '001e2fb8cad0c6bf', NULL, 1790354570644, 1792946570644, NULL, 1790354599334),
(344, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '3db42370e21cf59278a6614ece512e737a8d9b225f36710b1c29c6613599c355', '001e2fb8cad0c6bf', NULL, 1790354709647, 1792946709647, NULL, 1790354713741),
(345, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '502b9ab2d147be0c6c1a7250cbd64b88a3785b7366164efc7d05b95501f11d83', '001e2fb8cad0c6bf', NULL, 1790354981851, 1792946981851, NULL, 1790355009791),
(346, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'b3c4b8c622bfca7d754dd22dee59ed407f2868d086bf6ef730db7571fce9b38b', '001e2fb8cad0c6bf', NULL, 1790357344334, 1792949344334, NULL, 1790357433104),
(347, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '727213e472107218799920c2ad8bc3df63e02180537f831f05aa10acd2b6c4c5', '001e2fb8cad0c6bf', NULL, 1790357490241, 1792949490241, NULL, 1790357493196),
(348, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'd89db958bc4cd1e50e7ccbe2cf68cef7cd0aea6e09f41a2a690c5c9407487bac', '001e2fb8cad0c6bf', NULL, 1790357673491, 1792949673491, NULL, 1790357687894),
(349, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'bd0160905fbe9ef6fe6f33fcaf7bfdf85a858153ce04806971442b448e34f254', '001e2fb8cad0c6bf', NULL, 1790358498777, 1792950498777, NULL, 1790358607281),
(350, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '64f2556bdf3ae67819f81eb862112c24e2839cb9eadfe466d2b63635d7ba9ece', '001e2fb8cad0c6bf', NULL, 1790358750567, 1792950750567, NULL, 1790359552414);
INSERT INTO `auth_tokens` (`id`, `user_id`, `token_hash`, `device_id`, `device_label`, `created_at`, `expires_at`, `revoked_at`, `last_used_at`) VALUES
(351, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'df5d94e9958036cae0df9adcb3101ae01e625d8a36302070f24dc8ea9de1e63a', '001e2fb8cad0c6bf', NULL, 1790361477377, 1792953477377, NULL, 1790361502132),
(352, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '728d27c3f5717045fe13d3555f9fa533cbe68dafaf0096009aa8fed0462ec031', '001e2fb8cad0c6bf', NULL, 1790361623913, 1792953623913, NULL, 1790362158430),
(353, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'e9d8e86cc58d441146aea9f038332ec56c49a1c98990993e6acd46d74e674f97', 'e46a254c01d5555a', NULL, 1790367224039, 1792959224039, NULL, 1791211434052),
(354, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '591a347109a129f99421b63f55e4cb48fcab876644ba874861a3865c69ba24e8', 'b888c4f1ae152a5e', NULL, 1790367537249, 1792959537249, NULL, 1790513686047),
(355, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '9f539c226366acf45cc168ea80cf668c00418a56ee72370ec9d06f32194fe4d0', 'ae18b87304bd1fc9', NULL, 1790413803321, 1793005803321, NULL, 1791217117321),
(356, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '62943ae7565ed417978d40835bb402b834cf73a5bf2a2bab24cdc5a419f91d1d', '12c78af45489d46f', NULL, 1790439231105, 1793031231105, NULL, 1791204171408),
(357, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'a4b33f325e0d5ec59ffd96e3d6cbb82d7f9016acbf63f9e94771259327ca75ed', '6d618c4007ad31ea', NULL, 1790490827349, 1793082827349, NULL, 1791190915614),
(358, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'a10885525dc88b373fff1c6d58d27dcbe60d147b3afa38035468f3645f49b6db', '08c7788e1f261660', NULL, 1790494976720, 1793086976720, NULL, 1791178284793),
(359, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c0bcaac381f2c9366946d907b95f445b8274c32301927463db11aa3ba513b636', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790505783277, 1793097783277, NULL, 1790517020437),
(360, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5662fb01edbaa66b82176a3ab090639aee757a7ac426b58dd6d8bf6de7f48db1', '6fe6577503d2ecec', NULL, 1790513765803, 1793105765803, NULL, 1790831189347),
(361, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'f8dc1f690b90af7520b6f06bcd8b6f2c22928d2d2308f16f3b84989c9a246e01', 'e2790dfb42514b4e', NULL, 1790522930057, 1793114930057, NULL, 1791215178380),
(362, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '5aca1f3f30a7c06a8f3e13cd42c357b2b138ca0cf01967d7806c5481f3df2549', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790547600556, 1793139600556, NULL, 1790596828265),
(363, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '348c8c8a9a602de5dc52977da809b5d5aad319b29aa408f69d14f36ab5379c6b', '181cf6fea9d55e16', NULL, 1790581122821, 1793173122821, NULL, 1791217013439),
(364, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c92b3aba2e2a4dead1eb1b48f123da3c85f4e44f28b0d1d770489de8921ba39f', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790635768227, 1793227768227, NULL, 1790636523380),
(365, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ffe8366d064da1b280c17be0e9d5a9a7adff20db80495b9788c2ac1a931fa7d4', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790636532950, 1793228532950, NULL, 1790636573937),
(366, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '813bc2dff084a96c81f39ae1cc0cad135f2076ae1ca6d8338a48e35c591b4528', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790636586696, 1793228586696, NULL, 1790695123082),
(367, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'aa505317ef3d3eb7894747c27081b047507604dac8e28cc8cb3793198309febd', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790700759656, 1793292759656, NULL, 1790713580381),
(368, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c5d1c5203dbab716e2a0cc9cdcfbd6cdb5bdff0c1bbaf582dcc674016a27e03b', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790784880130, 1793376880130, NULL, 1790791771999),
(369, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0e7fbec1a5b4ed14b4a89c88e26ea8ba362a3251370ee90b4af8286b19686a77', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790803022032, 1793395022032, NULL, 1790803245050),
(370, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b1ce8e2ab6b2f7bd4b2bdc3379c72bc7beb1a28029264ed9ceddd47fffe1475b', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790803251688, 1793395251688, NULL, 1790843486795),
(371, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '100ce8cadde1f227b07aca1d39f8c90f54c09c272f277684b04a3474f41a2638', '001e2fb8cad0c6bf', NULL, 1790808135462, 1793400135462, NULL, 1790808150714),
(372, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'e167e41be3232ee342f1a482bdc9c57c323cd18688112a19f86f83482b50b2be', '001e2fb8cad0c6bf', NULL, 1790808140715, 1793400140715, NULL, 1790808213449),
(373, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '06e728927f77f703e01d7e73102079c21fc7b709f0b6ecd9e09fc791d653fe34', '001e2fb8cad0c6bf', NULL, 1790808258246, 1793400258246, NULL, 1790808273702),
(374, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '8c6ff5d5c63fc3bcc8b2c9dd6fba25d751403e66c3d7a7b13f5ac917717cf6b6', '001e2fb8cad0c6bf', NULL, 1790808271078, 1793400271078, NULL, 1790808281550),
(375, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '1402e242b1becf62bd47e2044364e54f79b198c004369ae09e7eb3e8d36108a4', '001e2fb8cad0c6bf', NULL, 1790808341845, 1793400341845, NULL, 1790808368525),
(376, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '4e8affa0b2e015399210e50348e916c6a0705f770d7707ccf6b9a7030f475aeb', '001e2fb8cad0c6bf', NULL, 1790808528782, 1793400528782, NULL, 1790808534414),
(377, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'e871fa1315e2ec6bf2a84f701b865fdf8431e91976a4fd517a728f63eb525808', '001e2fb8cad0c6bf', NULL, 1790808674677, 1793400674677, NULL, 1790808693420),
(378, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '564af9703fe8f820c6f73cb5abfb94d4e73bb70b3118632479e4c613fd507d7b', '001e2fb8cad0c6bf', NULL, 1790809186863, 1793401186863, NULL, 1790809205693),
(379, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'acaf4da7648034d1655adde613eacdf9266887733fb2b98b9024ea0983af8b2f', '001e2fb8cad0c6bf', NULL, 1790809269149, 1793401269149, NULL, 1790810226865),
(380, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '58266e46aa30a5b08593c45b125f1c43b92690e3746a44ccd33ce1c3794f28a6', '422334E6-C960-4DCE-A9A9-7FD29C875197', 'iPhone (iOS 27.0.1)', 1790809679572, 1793401679572, NULL, NULL),
(381, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '98e396c9c279579d1ebddff766e35026de3a77644d70cbf0a7a63e634704e877', '422334E6-C960-4DCE-A9A9-7FD29C875197', 'iPhone (iOS 27.0.1)', 1790812910753, 1793404910753, NULL, NULL),
(382, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '740d0f02cb90b3f1d358dec34d2bea4c2264e68c96af1fd4199d77d3c9082b75', 'b888c4f1ae152a5e', NULL, 1790831466168, 1793423466168, NULL, 1790845932893),
(383, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'cbf73fa41d338e0b84e1e08870cd69da6a3a7c3df178153619968e9c76bd9ee1', '422334E6-C960-4DCE-A9A9-7FD29C875197', 'iPhone (iOS 27.0.1)', 1790845527497, 1793437527497, NULL, NULL),
(384, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3e05ff634caf0200364411ca23487e50c7735caa459514e25e845d86aefd8a04', '82F08122-E332-489C-878D-C78B9E51FA28', 'iPhone (iOS 27.0.1)', 1790845859988, 1793437859988, NULL, NULL),
(385, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1ffcc5bee171eeddaac27af755bdd6568945da9aaa43290705ed55c767a32e66', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790846146275, 1793438146275, NULL, 1790877498275),
(386, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '7ffc903edede7e1d4c2873fb5d011c2fc38b3052c455d4b813f2266d34365f1a', 'b888c4f1ae152a5e', NULL, 1790886328100, 1793478328100, NULL, 1790886437101),
(387, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b7bb8e18e90d940dd689a0a9bedf64f8f0f4b921eb022766e01b29a7a164a725', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790886428168, 1793478428168, NULL, 1790933085755),
(388, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '48122a0a9ac7056c461bbc5a7e36df7dd4b75a8489f6c22635991e0fd69a207a', '6fe6577503d2ecec', NULL, 1790887237466, 1793479237466, NULL, 1791206913266),
(389, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b1446a3323ab25924ed9b5ada2813777cbadbf2985b8a812d9fb202655e6ef35', '749EE43F-035D-4B4F-B8AA-526BF03D6C71', 'iPhone (iOS 27.0.1)', 1790889602309, 1793481602309, NULL, NULL),
(390, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a1b7efe8604929d964036a9bd61516961ac6b3dc2e8eacd1b445bab4a6911a57', 'AF8F65B1-A1D9-463E-9D74-A69B222EDB92', 'iPhone (iOS 27.0.1)', 1790929815039, 1793521815039, NULL, 1791034039909),
(391, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0fd1c46ffa88564091c3662644d2da7335fe228b850760c60fe3c130012c10e8', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790933090885, 1793525090885, NULL, 1790933763992),
(392, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '15a24d10fddee2707a4f474db5931f219269552c9c2aebd300b34f8ae6f6e002', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790937603048, 1793529603048, NULL, 1790937649301),
(393, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b06d1fc5c02f24df99e7a56b83f455acc8ec234da339a68c090e2bc3398773d1', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790937664138, 1793529664138, NULL, 1790938361400),
(394, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '48ccd00873b129dfbd2e316a4b278288f07431e1007011ce349126a47883362d', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790938366679, 1793530366679, NULL, 1790938408166),
(395, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '35542f1c9fedd83bd9bc3709964c6fa98f1aa427666bccfdd77297f8e7cec199', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790938412425, 1793530412425, NULL, 1790938577410),
(396, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9e0f6d5c87b47f8745f6b9cafeef528604d226d86c601a54d50692d21bf607df', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1790938643584, 1793530643584, NULL, 1790955510448),
(397, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'cf3bffd09e3103ea8ae0bccb3d5e150943a09b36306f60c19d183efc1c402844', 'b168ba510453926d', NULL, 1790979836233, 1793571836233, NULL, 1791214496899),
(398, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e5e0e5f12bc2f210dadf5cfd77f739db264351ee28a09286a2b6945a259d79dd', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1791189503170, 1793781503170, NULL, 1791189725040),
(399, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '365c09931dc55192a68440e1eace77ca7507efbcbc09f3d558f9ee65e6b6bcee', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1791190009527, 1793782009527, NULL, 1791190034069),
(400, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c92abb2e03d060262cc340c015f2a9ab6ffc8a9bdd7cc641d37736abb1827b82', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1791190042895, 1793782042895, NULL, 1791191030922),
(401, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'fe5dde67792503da232b7dee16dea502b6626b363d57fda6bae0f98ea80baf29', '63c0cb8d-550a-462a-97ec-a61295d01021', 'Navigateur — Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36', 1791191043835, 1793783043835, NULL, 1791193214714);

-- --------------------------------------------------------

--
-- Structure de la table `budgets`
--

CREATE TABLE `budgets` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `category_id` char(36) NOT NULL,
  `period` varchar(32) NOT NULL,
  `limit_amount` bigint(20) NOT NULL,
  `currency_code` char(3) NOT NULL,
  `start_date` bigint(20) DEFAULT NULL,
  `end_date` bigint(20) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `budgets`
--

INSERT INTO `budgets` (`id`, `user_id`, `category_id`, `period`, `limit_amount`, `currency_code`, `start_date`, `end_date`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('0aa1f647-63d6-4ed0-9539-703a6953d891', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '0350b2f0-d812-4120-9b5d-aa8e56c7ea92', 'MONTHLY', 200000, 'EUR', 1788213600000, 1790719200000, 1790351955056, 1790353426171, NULL, 1),
('11e2e075-2dbc-4914-9ba2-ec2d968056b3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd632080a-26ce-4dd4-a56e-f44a509a96b9', 'MONTHLY', 1000000, 'XOF', 1790204400000, 1792882799000, 1790243408194, 1790244001119, NULL, 3),
('59b381a9-0c44-46db-a0ab-9c19b8f3b9fe', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'MONTHLY', 2500000, 'XOF', 1790204400000, 1792882799000, 1787216848655, 1790243931651, NULL, 7),
('6065f7f8-3c22-4e46-b68c-20fc230c3a18', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 'MONTHLY', 1500000, 'XOF', 1790636400000, 1793314799000, 1790244988185, 1790244991167, NULL, 1),
('69534496-6252-47ca-a7dc-6d1cd20caec4', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'e43608a5-6fca-44ec-963c-ab789e800161', 'MONTHLY', 20000, 'USD', 1790535600000, 1791054000000, 1791008329333, 1791040229612, NULL, 1),
('a8559082-f609-4442-b2b0-713833d4ac5a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 'MONTHLY', 5700000, 'XOF', 1789599600000, 1792882799000, 1790243241513, 1791191994181, NULL, 14),
('ee134983-092d-4a36-8c9b-36dac80cd0d7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 'MONTHLY', 1700000, 'XOF', 1789772400000, 1792882799000, 1787325181080, 1791192085112, NULL, 15);

-- --------------------------------------------------------

--
-- Structure de la table `categories`
--

CREATE TABLE `categories` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `icon` varchar(64) NOT NULL,
  `color_argb` bigint(20) NOT NULL,
  `type` varchar(32) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `categories`
--

INSERT INTO `categories` (`id`, `user_id`, `name`, `icon`, `color_argb`, `type`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('0350b2f0-d812-4120-9b5d-aa8e56c7ea92', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'essence', 'TRANSPORT', 4293870660, 'EXPENSE', 1790351928596, 1790353426028, NULL, 1),
('05364e12-8096-471b-9438-d510e5fbffc3', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'test cash', 'SALARY', 4293870660, 'EXPENSE', 1790828996569, 1790835052310, NULL, 1),
('0557e9d8-9a1a-4f4a-8e41-ac7af15b3032', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Test', 'HEALTH', 4293675161, 'EXPENSE', 1791130053683, 1791181009135, NULL, 1),
('0594a349-03e2-4886-b6da-e6c0dc574d15', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Prêt accordé', 'LOAN', 4279673674, 'EXPENSE', 1790750237289, 1790823757066, NULL, 1),
('06a7915c-05f4-40ba-a37f-fd3544ae4339', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Emprunt reçu', 'LOAN', 4292617766, 'INCOME', 1786570797319, 1786570797319, NULL, 1),
('09404dda-c202-4f95-8a82-8836ff7f3bfb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Internet', 'INTERNET', 4279150057, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('09523189-b7dc-43e6-a2dc-260899ad45b5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Prêt accordé', 'LOAN', 4279673674, 'EXPENSE', 1787923504031, 1788277955099, 1788277955099, 2),
('0d8805a3-1d31-4a01-bf39-0807657aa848', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Denial', 'OTHER', 4293870660, 'EXPENSE', 1786911232329, 1786911232329, NULL, 1),
('0f02a865-3ad2-4a61-839f-205e47479c77', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Transport', 'TRANSPORT', 4280640491, 'EXPENSE', 1787923504031, 1788277955141, 1788277955141, 2),
('0f4c241a-e884-43d6-ba20-0113b4acf1ba', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'test cash', 'SALARY', 4279286145, 'EXPENSE', 1790829013548, 1790835052311, NULL, 1),
('22222222-2222-2222-2222-222222222222', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Test Postman', 'OTHER', 4283215384, 'EXPENSE', 1756000000000, 1756000000000, NULL, 1),
('235567cd-f5ec-4096-9cbb-57e45cd39f02', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Nourriture', 'FOOD', 4294286859, 'EXPENSE', 1787923504031, 1788277955093, 1788277955093, 2),
('2c1f02b9-05c3-4ebe-99ed-54c139279e4a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Transport', 'TRANSPORT', 4280640491, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('2c7f27e3-fe50-41ec-8dc3-7a5b3efdfe0a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Frais et commissions', 'FEE', 4287774734, 'EXPENSE', 1787923504031, 1788277955068, 1788277955068, 2),
('301cb0ef-dd1f-4a3a-b83d-f4c5ada8f1e3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Test Postman', 'ic_category_default', -16776961, 'EXPENSE', 1787742219000, 1788277955135, 1788277955135, 2),
('32525ac6-aaa5-4d61-b605-dbad902ac737', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Remboursement d\'emprunt', 'LOAN', 4292617766, 'EXPENSE', 1787923504031, 1788277955104, 1788277955104, 2),
('37a1e4ad-b8ab-40a8-9ce0-a713bef1f955', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Shopping', 'SHOPPING', 4286331629, 'EXPENSE', 1787923504031, 1788277955128, 1788277955128, 2),
('3d512d37-6425-43a4-81ac-81ede7434f48', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Revenu', 'SALARY', 4279286145, 'INCOME', 1786656040735, 1786656040735, NULL, 1),
('420bfc42-9e1a-494a-ba85-10b52cd3b8dd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Achat en ligne', 'SHOPPING', 4293870660, 'EXPENSE', 1786907863359, 1786907863359, NULL, 1),
('420f5bd6-d740-4ecb-8a57-0c6a3fb8f83c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Santé', 'HEALTH', 4292617766, 'EXPENSE', 1787923504031, 1788277955123, 1788277955123, 2),
('425749bf-cc1e-4de1-bf48-961529658997', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Plaisir alimentaire', 'FOOD', 4293870660, 'EXPENSE', 1787241629503, 1787241629503, NULL, 1),
('45f98dc0-89d4-41a8-a6d0-15d5473d07dc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Cadeaux', 'GIFTS', 4293675161, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('465c7da5-5a05-4f49-83b6-468bc4d22dd8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Test Postman', 'ic_category_default', -16776961, 'EXPENSE', 1787656743000, 1788277955135, 1788277955135, 2),
('55d5477c-eb59-4f21-8492-6975a3ae4e46', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Divers', 'OTHER', 4284773515, 'INCOME', 1786570797319, 1786570797319, NULL, 1),
('5e33f332-815e-4bd4-800c-c61ded5b5854', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Maison', 'HOME', 4279286145, 'EXPENSE', 1787923504031, 1788277955085, 1788277955085, 2),
('5f33c448-ef2e-4a44-b803-fe4ec2020e7b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Internet', 'INTERNET', 4279150057, 'EXPENSE', 1787923504031, 1788277955078, 1788277955078, 2),
('645303e4-c190-4c39-b258-b91e6316b709', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Électricité', 'ELECTRICITY', 4294286859, 'EXPENSE', 1787923504031, 1788277955047, 1788277955047, 2),
('68f9d5a7-91a1-4027-8339-7849f09ff9bb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Service', 'LOAN', 4279286145, 'EXPENSE', 1790160331539, 1790162932888, NULL, 1),
('6b166157-883f-44f1-a2ef-62007bea6881', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Outils', 'LOAN', 4279286145, 'EXPENSE', 1789478891674, 1789479114251, NULL, 1),
('6c22c352-d32b-4d6f-9809-8e01de32e443', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Recharge', 'LOAN', 4279548070, 'EXPENSE', 1786908254020, 1786908254020, NULL, 1),
('70307aa9-3454-4864-b81a-a423354532ee', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Recharge', 'LOAN', 4279548070, 'INCOME', 1786908296310, 1786908296310, NULL, 1),
('72655fef-7f70-43a6-a0cb-6f3e634b9123', '5e1c8db5-c66b-48bc-8b53-d43a1fc8e701', 'gas', 'OTHER', 4293870660, 'EXPENSE', 1790391409519, 1790512040633, NULL, 2),
('76ff068e-8efa-4655-8c9f-6a4ce9577642', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Divers', 'OTHER', 4284773515, 'INCOME', 1787923504031, 1788277955016, 1788277955016, 2),
('811ea896-732a-44d8-91c5-1dbc7e974624', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Prime', 'SALARY', 4279548070, 'INCOME', 1787438425649, 1787438425649, NULL, 1),
('82c2282a-f15f-4976-b380-e1a6331280d8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Retrait', 'SALARY', 4293870660, 'EXPENSE', 1786572173679, 1786572173679, NULL, 1),
('843152ec-f4d6-4e84-9a79-f72eca40dee8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Remboursement de prêt reçu', 'LOAN', 4279673674, 'INCOME', 1786570797319, 1786570797319, NULL, 1),
('8e4f9cf0-c631-489d-be3a-757f533fb3f1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Divers', 'OTHER', 4284773515, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('8ff6b4d1-2775-48d0-93a7-dda7b93b5536', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Salaire', 'SALARY', 4278217807, 'INCOME', 1786570797319, 1786570797319, NULL, 1),
('95679e67-8988-47b7-9b8a-938882f3fef8', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'plop', 'SHOPPING', 4293870660, 'INCOME', 1790829027933, 1790835052312, NULL, 1),
('969728c5-9d6f-42d4-9e40-bb0f1827f955', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Cadeaux', 'GIFTS', 4293675161, 'INCOME', 1790937948584, 1790938369582, NULL, 1),
('999243ea-2013-4d43-8863-f876c3dbfd2e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Dépense', 'SALARY', 4293870660, 'EXPENSE', 1786655970664, 1786655970664, NULL, 1),
('a0daf981-8445-405e-b8a1-69729b8e67c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Prêt accordé', 'LOAN', 4279673674, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('a146399c-2ebb-40b2-8b01-2073528b1898', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Propriété', 'SALARY', 4282090230, 'EXPENSE', 1786822231585, 1786822231585, NULL, 1),
('a51ae322-6b10-4851-a566-1a0fb38a4a91', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Eau', 'WATER', 4278630100, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('a6b4aa94-b85f-4ded-99d4-fda7322747d7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Divers', 'OTHER', 4284773515, 'EXPENSE', 1787923504031, 1788277955008, 1788277955008, 2),
('a8a6a002-3519-4816-8c11-09486c31d655', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Dépôt', 'SALARY', 4279286145, 'INCOME', 1786828332850, 1786828332850, NULL, 1),
('a8f0dd81-d119-40ba-bfd0-4ef04f87b566', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Remboursement de prêt reçu', 'LOAN', 4287323382, 'INCOME', 1790920895980, 1791148540644, NULL, 3),
('aca0395a-c35b-4354-947c-86d286cc3319', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Salaire', 'SALARY', 4278217807, 'INCOME', 1787923504031, 1788277955116, 1788277955116, 2),
('ace50dc9-eb9e-4ade-b7ce-911e61f3f541', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'kkkkkk', 'OTHER', 4279286145, 'EXPENSE', 1787692450372, 1789479114235, 1789479114235, 2),
('af588aa1-c872-4a5e-bf5b-abdec987c7d6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Emprunt reçu', 'LOAN', 4292617766, 'INCOME', 1787923504031, 1788277955056, 1788277955056, 2),
('b3806b64-d23f-4314-af7e-a2f84cfb16b8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'rrrrr', 'OTHER', 4279286145, 'EXPENSE', 1787741644453, 1789479114245, 1789479114245, 2),
('b44c3c45-73e5-4877-9a72-d19e235174c1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Eau', 'WATER', 4278630100, 'EXPENSE', 1787923504031, 1788277955025, 1788277955025, 2),
('b572f3d7-d60c-42fe-b794-51dc9f1181df', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Éducation', 'EDUCATION', 4279673674, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('ba1dd949-ab33-4ffd-a8db-31581bd650c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'test2', 'FOOD', 4279286145, 'EXPENSE', 1787664221988, 1787690731617, 1787690731617, 3),
('be4274ce-0aa2-4b32-aebc-362efa946023', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Shopping', 'SHOPPING', 4286331629, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('c3791d42-62f5-4719-bb17-0ba7b793fcb3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Éducation', 'EDUCATION', 4279673674, 'EXPENSE', 1787923504031, 1788277955032, 1788277955032, 2),
('c3eb7499-816c-4822-826e-9f0368ceaedb', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Frais et commissions', 'FEE', 4287774734, 'EXPENSE', 1790581214537, 1790651994239, NULL, 1),
('c5373db8-695f-4b73-ba20-6d2ec4e1e67f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Cadeaux', 'GIFTS', 4293675161, 'EXPENSE', 1787923504031, 1788277954982, 1788277954982, 2),
('c60126a4-d70c-4230-9014-2f5e395bd487', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Électricité', 'ELECTRICITY', 4294286859, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('c6262555-d57c-4cc3-a1d5-6d682eb4022d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Crédit Téléphonique', 'LOAN', 4293870660, 'EXPENSE', 1787395865820, 1787395865820, NULL, 1),
('c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Frais et commissions', 'FEE', 4287774734, 'EXPENSE', 1786634220435, 1786634220435, NULL, 1),
('cd1d8523-9cb0-4e9c-abb1-aa2aef57bb6a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Remboursement de prêt reçu', 'LOAN', 4279673674, 'INCOME', 1787923504031, 1788277955110, 1788277955110, 2),
('d0745954-8b68-48a4-8908-7f500dd89d3d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Test Postman', 'ic_category_default', -16776961, 'EXPENSE', 1787742889000, 1788277955135, 1788277955135, 2),
('d5471c1f-5500-418f-8a74-2831324c655c', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'test@gmail.com', 'GIFTS', 4282090230, 'EXPENSE', 1790809306424, 1790809447092, NULL, 1),
('d632080a-26ce-4dd4-a56e-f44a509a96b9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Maison', 'HOME', 4279286145, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('d80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Remboursement d\'emprunt', 'LOAN', 4292617766, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('d82d212d-c355-4c24-8c8d-2313ab03fe0b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Nourriture', 'FOOD', 4294286859, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1),
('e04fdeb1-cc4f-4015-b7b2-9543f3c49f8d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'avion', 'TRANSPORT', 4279286145, 'EXPENSE', 1787692997891, 1787692997892, NULL, 1),
('e43608a5-6fca-44ec-963c-ab789e800161', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Test say 3', 'OTHER', 4286893078, 'EXPENSE', 1790578607074, 1790584945296, NULL, 1),
('eddc8008-9e31-4ff9-ac07-ac3d50401a8f', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'wifi', 'INTERNET', 4279286145, 'EXPENSE', 1790845389529, 1790866982707, NULL, 1),
('ef3e4efb-662a-4ced-9e91-ea6902c08cdd', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'test@gmail.com', 'SALARY', 4279286145, 'EXPENSE', 1790358619576, 1790358754386, NULL, 1),
('f0fe0918-0b8f-430e-abaf-a8e3075edf05', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Santé', 'HEALTH', 4292617766, 'EXPENSE', 1786570797319, 1786570797319, NULL, 1);

-- --------------------------------------------------------

--
-- Structure de la table `financial_plans`
--

CREATE TABLE `financial_plans` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `description` text DEFAULT NULL,
  `available_amount` bigint(20) NOT NULL,
  `target_amount` bigint(20) DEFAULT NULL,
  `period_type` varchar(32) NOT NULL,
  `start_date` bigint(20) DEFAULT NULL,
  `end_date` bigint(20) DEFAULT NULL,
  `icon` varchar(64) NOT NULL,
  `color_argb` bigint(20) NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `financial_plans`
--

INSERT INTO `financial_plans` (`id`, `user_id`, `name`, `description`, `available_amount`, `target_amount`, `period_type`, `start_date`, `end_date`, `icon`, `color_argb`, `status`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('2a6f11ca-dd37-40d9-8773-713dee4067e7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Voyage', 'Mon compte de Voyage', 100000000, NULL, 'MONTHLY', 1787785200000, 1787957999000, 'PLANE', -12404328, 'ACTIVE', 1787846742931, 1787846771480, 1787846771480, 2),
('32eac8e3-4271-4c54-b657-491268bc271a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Ça marche', NULL, 1000000, NULL, 'NONE', NULL, NULL, 'WALLET', 4279286145, 'ACTIVE', 1787692131353, 1787772300571, 1787772300571, 4),
('4bc79ed7-d056-4cd7-9941-a667a3fdee58', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Les dépenses du mois (10/26)', NULL, 14390000, NULL, 'NONE', NULL, NULL, 'WALLET', -15681151, 'ACTIVE', 1790191947892, 1790192126134, NULL, 3),
('5377a766-2541-448b-9d0d-3ed07b0bd7c4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Mon Test', NULL, 10000000, NULL, 'MONTHLY', 1787691524159, 1787691524159, 'WALLET', 4279286145, 'ACTIVE', 1787691547905, 1787691607934, 1787691607934, 2),
('8c489b7a-79ef-47d8-8c0e-4a88da4470f4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Les dépenses du mois (08/26)', NULL, 14000000, NULL, 'NONE', NULL, NULL, 'WALLET', -15681151, 'ARCHIVED', 1787090341634, 1788212777570, NULL, 5),
('bec0b37e-da5f-4ba4-a846-d482ba7c2350', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Les dépenses du mois (09/26)', NULL, 16000000, NULL, 'NONE', NULL, NULL, 'WALLET', -15681151, 'ARCHIVED', 1788212768675, 1790191966129, NULL, 4),
('c53fa721-570e-4f8b-bb9f-ee5254fde2dd', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Trip', NULL, 50000, 48000, 'MONTHLY', 1791192810570, 1791192810570, 'MOTORCYCLE', 4293675161, 'ACTIVE', 1791192837927, 1791217003836, NULL, 1);

-- --------------------------------------------------------

--
-- Structure de la table `financial_plan_items`
--

CREATE TABLE `financial_plan_items` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `plan_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `actual_amount` bigint(20) DEFAULT NULL,
  `category_id` char(36) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `planned_date` bigint(20) DEFAULT NULL,
  `priority` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `transaction_id` char(36) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `financial_plan_items`
--

INSERT INTO `financial_plan_items` (`id`, `user_id`, `plan_id`, `name`, `amount`, `actual_amount`, `category_id`, `description`, `planned_date`, `priority`, `status`, `transaction_id`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('0ddf272f-aaec-40c9-a348-f05b482433fa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Réparation de Moto', 1000000, NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', NULL, NULL, 'IMPORTANT', 'DONE', NULL, 1787090881474, 1788182302539, NULL, 2),
('1750ae6a-9c92-4ad4-ab37-e1dbcf2c11a9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Vêtements', 2000000, NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', NULL, NULL, 'OPTIONAL', 'TO_PLAN', NULL, 1790191950231, 1790192299236, 1790192299236, 2),
('181ddd2b-7bce-4d04-b94c-882fe2391afc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Cadeau de Maman', 1000000, 1000000, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'IMPORTANT', 'DONE', 'dfd973ba-7875-4ec9-82db-9fa6f2a4d86b', 1788212769585, 1788296742348, NULL, 2),
('1d9d90c4-1fc6-485b-ad9e-7aa1783baedd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Investissement', 1000000, NULL, NULL, NULL, NULL, 'OPTIONAL', 'TO_PLAN', NULL, 1788216071665, 1788216071665, NULL, 1),
('1e26531c-24e2-4315-b679-f3cf049609b6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Connexion internet', 1200000, 1200000, '09404dda-c202-4f95-8a82-8836ff7f3bfb', NULL, NULL, 'IMPORTANT', 'DONE', 'f2c0980b-b7c7-44b9-805a-23168123a166', 1790191952485, 1790242786703, NULL, 3),
('206c8b21-de22-4aa1-926f-9431630ae09e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Rembourser les emprunt de Maman', 2000000, NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1788212769482, 1788213092280, 1788213092280, 2),
('3463e92b-675b-4bce-9a15-d4c1def099c1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Investissement', 1500000, NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', NULL, NULL, 'OPTIONAL', 'DONE', NULL, 1790191950944, 1790244126581, NULL, 5),
('387d85c7-94d1-4033-87ea-e80e721a4b76', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Restauration', 3200000, NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1787090723782, 1787216899263, NULL, 1),
('475fbfb5-b73a-444f-98a6-7c933cc65661', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Aide Famile', 800000, NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'OPTIONAL', 'DONE', NULL, 1790192978710, 1790244126526, NULL, 4),
('5387d7e7-16b4-4b61-9a92-7a472da8d982', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Rendez-vous avec Aïcha', 2000000, NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1788212768867, 1788258893937, NULL, 2),
('593ec7f5-1f99-4732-8c80-caea8b686325', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Restauration', 2485000, NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1788212769688, 1788214466350, NULL, 2),
('69f94fdd-9d83-4fb3-abfd-a975b0b964b5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Rendez-vous avec Aïcha', 2000000, NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1787090824929, 1787090824929, NULL, 1),
('6cfdecc8-8cf4-4d1a-b831-935af72ff2ee', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Épargne Mariage', 2000000, NULL, '6c22c352-d32b-4d6f-9809-8e01de32e443', NULL, NULL, 'IMPORTANT', 'DONE', NULL, 1787091303959, 1788182285151, NULL, 2),
('73991e14-33f2-4b75-a67c-d79694ea4f3f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Connexion internet', 1250000, 1250000, '09404dda-c202-4f95-8a82-8836ff7f3bfb', NULL, NULL, 'IMPORTANT', 'DONE', 'bd93b1d2-56d7-480e-a4c7-1479fb83d83f', 1787130093995, 1787213966148, NULL, 1),
('756b0b21-6655-4742-9017-47a42afcfea6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Cadeau de Maman', 1000000, 1000000, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'IMPORTANT', 'DONE', '4c247258-24db-4046-b918-bf796e0622b8', 1790191953099, 1790242786705, NULL, 2),
('7bb385ed-ff21-4a11-afdc-2d8102c02d4b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Épargne Mariage', 2000000, NULL, '6c22c352-d32b-4d6f-9809-8e01de32e443', NULL, NULL, 'OPTIONAL', 'TO_PLAN', NULL, 1788212769174, 1788214195082, 1788214195082, 3),
('89ba367b-ac88-4376-b535-662668e8078f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Remboursements des prets', 1647400, NULL, NULL, NULL, NULL, 'IMPORTANT', 'DONE', NULL, 1790192630861, 1790242786708, NULL, 3),
('8b2a5737-c823-4c6b-bbbc-db9e5e0d615c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Cadeau de Maman', 1000000, 1000000, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'IMPORTANT', 'DONE', '82d22645-752e-48c1-9baf-840944d94273', 1787090550427, 1787214953100, NULL, 1),
('973f91fe-5ff8-4117-9ec2-55c5aa9f9fad', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Rembourser les emprunt de Maman', 2000000, 2000000, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', NULL, NULL, 'IMPORTANT', 'DONE', '6222af30-cc43-4c80-bb73-e769c587838c', 1787090484108, 1787214930899, NULL, 1),
('9dbc2b9f-f8a5-4565-8e9d-b84d2aa03a78', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Transport', 1100000, NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', NULL, NULL, 'IMPORTANT', 'DONE', NULL, 1790191948489, 1790244729658, NULL, 5),
('a055b372-2b85-461b-bbe1-ad818b354ae3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Connexion internet', 600000, 600000, '09404dda-c202-4f95-8a82-8836ff7f3bfb', NULL, NULL, 'IMPORTANT', 'DONE', 'cc75e586-32f5-466b-bd90-d3b45afbe77e', 1788212769348, 1788264226753, NULL, 3),
('a34eb11f-814b-42a7-8378-a9244c1a744f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Carburant', 1000000, NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1788212769071, 1788212769071, NULL, 1),
('a7400501-13e8-4b14-bd59-77d4396e60f5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Rendez-vous avec Aïcha', 2000000, NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', NULL, NULL, 'OPTIONAL', 'DONE', NULL, 1790191951619, 1790242786706, NULL, 3),
('ba91b1e3-c28a-4747-9840-a727d4915d8d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Réparation de Moto', 1000000, NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1788212768968, 1788212871529, 1788212871529, 2),
('c03cd2cb-08ce-4436-9ac5-8d2b60bcf177', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Cadeau Familles', 1000000, NULL, NULL, NULL, NULL, 'OPTIONAL', 'TO_PLAN', NULL, 1788214360743, 1788214391283, NULL, 3),
('d3e905cd-bd1e-44e6-8b11-17083e93a406', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Tontine', 1000000, NULL, NULL, NULL, 1790118000000, 'IMPORTANT', 'DONE', NULL, 1790244340784, 1790244633727, NULL, 2),
('d4cf7f48-2263-4147-af2b-ba1f75faa5f8', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'c53fa721-570e-4f8b-bb9f-ee5254fde2dd', 'Abz', 8000, NULL, NULL, NULL, NULL, 'ESSENTIAL', 'TO_PLAN', NULL, 1791192855676, 1791217003834, NULL, 1),
('d646b377-13ba-428c-a571-4c378a0ffc74', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Restauration', 2500000, NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', NULL, NULL, 'IMPORTANT', 'DONE', NULL, 1790191949659, 1790242786707, NULL, 3),
('ec94a027-c402-4b61-a5cd-5d916bb1c819', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bec0b37e-da5f-4ba4-a846-d482ba7c2350', 'Vêtements', 2000000, NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', NULL, NULL, 'OPTIONAL', 'TO_PLAN', NULL, 1788214972517, 1788214972517, NULL, 1),
('fab79bbb-403d-483a-b980-f60c583d7705', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '8c489b7a-79ef-47d8-8c0e-4a88da4470f4', 'Carburant', 1000000, NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', NULL, NULL, 'IMPORTANT', 'TO_PLAN', NULL, 1787091229411, 1787091229411, NULL, 1),
('fed6d6df-e62a-468c-b5c2-23fb728e77bb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4bc79ed7-d056-4cd7-9941-a667a3fdee58', 'Frais d\'electricite', 1000000, NULL, 'd632080a-26ce-4dd4-a56e-f44a509a96b9', NULL, NULL, 'OPTIONAL', 'DONE', NULL, 1790191949090, 1790244739400, NULL, 5);

-- --------------------------------------------------------

--
-- Structure de la table `loans`
--

CREATE TABLE `loans` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `person_id` char(36) NOT NULL,
  `account_id` char(36) NOT NULL,
  `type` varchar(16) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `amount_repaid` bigint(20) NOT NULL,
  `remaining_amount` bigint(20) NOT NULL,
  `start_date` bigint(20) NOT NULL,
  `due_date` bigint(20) NOT NULL,
  `reason` varchar(32) NOT NULL,
  `reason_custom_text` varchar(255) DEFAULT NULL,
  `repayment_mode` varchar(32) NOT NULL,
  `description` text NOT NULL,
  `status` varchar(32) NOT NULL,
  `transaction_id` char(36) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1,
  `gifted_amount` bigint(20) NOT NULL DEFAULT 0,
  `gift_transaction_id` char(36) DEFAULT NULL,
  `gifted_at` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `loans`
--

INSERT INTO `loans` (`id`, `user_id`, `person_id`, `account_id`, `type`, `amount`, `amount_repaid`, `remaining_amount`, `start_date`, `due_date`, `reason`, `reason_custom_text`, `repayment_mode`, `description`, `status`, `transaction_id`, `created_at`, `updated_at`, `deleted_at`, `version`, `gifted_amount`, `gift_transaction_id`, `gifted_at`) VALUES
('109eecad-125e-4f2c-be4c-9ae030a972e3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9656c1b6-af21-4935-9497-00055b0c72d2', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 100000, 100000, 0, 1788130800000, 1788217200000, 'FINANCIAL_HELP', NULL, 'SINGLE', '', 'REPAID', '269b539d-38a4-4121-bfa0-752fc824706e', 1788214750499, 1788298607169, NULL, 2, 0, NULL, NULL),
('157484c5-d680-42eb-9e0f-64281a71b979', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f5894388-ffb3-4bb0-9614-5cbc028400be', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 200000, 200000, 0, 1789858800000, 1789945200000, 'OTHER', NULL, 'SINGLE', '', 'REPAID', 'b4b78cd1-d26b-4c19-9013-aa5ff30f57c5', 1790066008826, 1790076621035, NULL, 2, 0, NULL, NULL),
('1b2ee384-848b-4a29-b144-a2dfabd5d1fe', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '848b9cb6-434a-41cb-8b6e-cc5a877fd227', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'BORROWED', 2000000, 0, 2000000, 1786740734105, 1788130800000, 'OTHER', NULL, 'SINGLE', '', 'OVERDUE', '694a2c87-f68b-4535-8438-287bfecbb655', 1786740833775, 1790401188946, 1790401188946, 6, 0, NULL, NULL),
('1e46f6da-b059-46b4-bcd2-84077eee0a0a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cd5168e-c908-49e0-9208-40b2257a89b0', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'BORROWED', 30000, 0, 0, 1790841900000, 1790895600000, 'FINANCIAL_HELP', NULL, 'SINGLE', '', 'GIFTED', '737c095c-abfb-4f27-b1b0-975be4a5ce53', 1790848727881, 1790938370350, NULL, 2, 30000, '737c095c-abfb-4f27-b1b0-975be4a5ce53', 1790937948576),
('2c343910-495b-42e7-b8e6-ac4076a76b0a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0e912827-caa6-41bf-af06-8c654f14a562', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 1500000, 1500000, 0, 1789340400000, 1792070379315, 'OTHER', NULL, 'SINGLE', '', 'REPAID', '72db4f02-51ab-44f1-9096-ea240ac53ce1', 1789478516111, 1789479114100, NULL, 2, 0, NULL, NULL),
('34f2ca7c-f8d6-445b-a622-68228c2d012e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '32ab7d28-65eb-4d41-b374-8d04ce767f67', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 500000, 0, 500000, 1787220289086, 1798758000000, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', '84962ca8-9e10-4d9d-9296-0d56e28d09af', 1787220336406, 1787220336406, NULL, 1, 0, NULL, NULL),
('465622f5-7d0e-49cf-90f8-821b55ff6a51', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'd1ae9468-48dc-4d8a-a5f5-e9a48c35f0a5', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 1000000, 0, 1000000, 1790290800000, 1790550000000, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', '2765dfe4-21cd-4fd2-b8cf-aae40f25df41', 1790401350808, 1790401854857, NULL, 1, 0, NULL, NULL),
('52eb20f0-8668-4253-8bdd-8a1e8034c8ec', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'dd41f65d-b14e-47ac-b518-0f0749d6c944', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'BORROWED', 200000, 0, 200000, 1790463600000, 1790809200000, 'FINANCIAL_HELP', NULL, 'SINGLE', 'Je donne un avance de 3000F sur 5000F ca reste 2000F', 'ONGOING', '52ee1ba8-a096-4c72-8ff7-3c527ccda3ac', 1790507681646, 1790507681731, NULL, 1, 0, NULL, NULL),
('584e9697-f678-4247-9440-71c63ec9ab61', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '862cadb4-1f9b-442a-94fa-e38d14419d96', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'BORROWED', 5000, 5000, 0, 1789117281133, 1789340400000, 'OTHER', NULL, 'SINGLE', '', 'REPAID', '4ee66836-34d1-4b37-874b-836972904a7c', 1789117401545, 1789383160299, NULL, 2, 0, NULL, NULL),
('6107c50e-7b23-490f-aecb-692ad6c439ed', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3fcfa015-9351-4669-832c-bef003b2d277', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'BORROWED', 500000, 230000, 270000, 1790377200000, 1790809200000, 'FINANCIAL_HELP', NULL, 'SINGLE', '', 'OVERDUE', 'bdaf7d11-d89a-465d-a056-9e63b9ce69a7', 1790507373943, 1791191153030, NULL, 6, 0, NULL, NULL),
('691db119-b3f1-4d75-918c-fb887cf70290', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0e912827-caa6-41bf-af06-8c654f14a562', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 150000, 0, 150000, 1791140280000, 1791154800000, 'FINANCIAL_HELP', NULL, 'SINGLE', 'Je lui est acheter un cable type-c iphone', 'ONGOING', '56f9f232-d140-4151-afa4-4758b0144d83', 1791190831299, 1791190832200, NULL, 1, 0, NULL, NULL),
('6d60302d-a0c9-4752-acaa-3990dad51f0c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '0e912827-caa6-41bf-af06-8c654f14a562', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 200000, 200000, 0, 1789478670071, 1789513200000, 'OTHER', NULL, 'SINGLE', '', 'REPAID', 'ea2e4e4f-10a9-4203-8625-d523e1fedc59', 1789478712502, 1789725248744, NULL, 2, 0, NULL, NULL),
('71367108-8d71-4651-8769-4aa274dbad0d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f5894388-ffb3-4bb0-9614-5cbc028400be', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'BORROWED', 500000, 250000, 250000, 1790290800000, 1790809200000, 'FINANCIAL_HELP', NULL, 'SINGLE', 'Il n\'a pas de money', 'ONGOING', '05fd0b64-1452-4cf4-8267-40397ac3f5c4', 1790507887775, 1790507932318, NULL, 2, 0, NULL, NULL),
('84c2f96d-982a-4324-a782-b6b967ede88a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3fcfa015-9351-4669-832c-bef003b2d277', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 50000, 50000, 0, 1790377200000, 1790809200000, 'FINANCIAL_HELP', NULL, 'SINGLE', '', 'REPAID', 'c93e21c6-76c8-4991-b427-db157441b2aa', 1790506328809, 1790945634875, NULL, 4, 0, NULL, NULL),
('92db4805-3e2b-4343-8118-577cc7ce4248', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '097204d1-2212-4c27-ae35-46290684f831', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 1500000, 300000, 1200000, 1772319600000, 1798758000000, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', '94c288d5-9c45-4dfe-ae84-0708b145f7b6', 1786825408699, 1786825448501, NULL, 1, 0, NULL, NULL),
('99cca20d-39b9-4c03-9dfc-be7fdaa073fb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'bde248cb-42f6-44bb-a9d8-11cd928d05a9', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'LENT', 1000000, 0, 1000000, 1788476400000, 1798758000000, 'FINANCIAL_HELP', NULL, 'SINGLE', '', 'ONGOING', 'f9b924c0-538d-4457-bab1-92f9f10a6526', 1788558335169, 1788558336064, NULL, 1, 0, NULL, NULL),
('9e28b943-a942-48b9-8fad-f388f15ea2ce', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '03c0c6af-4ca3-426c-9d38-0935f430c0e4', '97d3f709-8069-4963-8c32-874009967823', 'LENT', 50000, 6000, 44000, 1790920875736, 1793512875736, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', 'f97c9de0-106b-45a0-9d79-2c7337adccc0', 1790920886227, 1790946050623, NULL, 3, 0, NULL, NULL),
('a44c2754-8bbf-4364-a5e0-13d89cb9c21b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cd5168e-c908-49e0-9208-40b2257a89b0', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 50000, 50000, 0, 1789117113229, 1789340400000, 'OTHER', NULL, 'SINGLE', '', 'REPAID', '8e67ec63-621e-48a6-8781-a0e6809bd644', 1789117202911, 1790673380127, NULL, 2, 0, NULL, NULL),
('a78e44f0-37a3-4c1f-8f7e-f856d54bc187', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '32ab7d28-65eb-4d41-b374-8d04ce767f67', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 500000, 0, 500000, 1768950000000, 1798758000000, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', 'c209db46-40a4-4f4a-b4eb-510413c4d7d9', 1786824974185, 1786824974185, NULL, 1, 0, NULL, NULL),
('b5681c3e-84cb-4fcf-8909-9e8d58743304', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '32ab7d28-65eb-4d41-b374-8d04ce767f67', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 1000000, 0, 1000000, 1786825114785, 1798758000000, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', 'b892c1a0-e2a0-42e8-904a-7e61ea55259c', 1786825153258, 1786825153258, NULL, 1, 0, NULL, NULL),
('e0a11624-40db-4b51-a85d-1067884d13c7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '33c2e440-3c9a-4552-897d-40ec35402f9d', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'BORROWED', 400000, 400000, 0, 1788217200000, 1792833809390, 'OTHER', NULL, 'SINGLE', '', 'REPAID', 'cbf91737-46eb-4027-b824-fb9b27a23e2a', 1790241928194, 1790243759409, NULL, 2, 0, NULL, NULL),
('f0b7a963-6c57-415a-a95b-612ed8410ecf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f5894388-ffb3-4bb0-9614-5cbc028400be', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'LENT', 200000, 0, 200000, 1789858800000, 1789858800000, 'EMERGENCY', NULL, 'SINGLE', '', 'ONGOING', '25fb2223-1545-4419-977f-8a5111849483', 1789934169163, 1789934606230, 1789934606230, 2, 0, NULL, NULL),
('f416f1a2-f955-4c83-830b-4f7d67e9d73e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3fcfa015-9351-4669-832c-bef003b2d277', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'LENT', 100000, 100000, 0, 1790204400000, 1790809200000, 'FINANCIAL_HELP', NULL, 'SINGLE', '', 'REPAID', 'c60b3e08-93cc-4c68-b2e7-b0b9a54e6a48', 1790334666181, 1790507035784, NULL, 2, 0, NULL, NULL),
('f7b41144-c985-404a-b346-58f88501b92c', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '03c0c6af-4ca3-426c-9d38-0935f430c0e4', '97d3f709-8069-4963-8c32-874009967823', 'LENT', 69000, 0, 69000, 1790750215043, 1793342215043, 'OTHER', NULL, 'SINGLE', '', 'ONGOING', '8b405ab1-980f-4dff-b535-de1b5494e202', 1790750237287, 1790823757498, NULL, 1, 0, NULL, NULL);

-- --------------------------------------------------------

--
-- Structure de la table `loan_payments`
--

CREATE TABLE `loan_payments` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `loan_id` char(36) NOT NULL,
  `account_id` char(36) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `date` bigint(20) NOT NULL,
  `note` text NOT NULL,
  `transaction_id` char(36) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `loan_payments`
--

INSERT INTO `loan_payments` (`id`, `user_id`, `loan_id`, `account_id`, `amount`, `date`, `note`, `transaction_id`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('0b3eba84-0871-40af-95e1-ea49b5bca3da', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6107c50e-7b23-490f-aecb-692ad6c439ed', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 25000, 1791140640000, 'Son contribution pour acheter Anana', '63ae06a4-b089-4fa3-949b-8bd941c9b9c2', 1791191145835, 1791191153508, NULL, 1),
('15ca34c6-0c9e-4995-ba5e-769a6bd22fec', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '157484c5-d680-42eb-9e0f-64281a71b979', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 200000, 1790031600000, '', '84585531-d597-4df8-b87d-c7fdd27abf47', 1790066116223, 1790076621470, NULL, 1),
('18ec8e11-1795-4bfe-bb74-2e9c44e1eb8d', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', '9e28b943-a942-48b9-8fad-f388f15ea2ce', '97d3f709-8069-4963-8c32-874009967823', 6000, 1790920891883, '', '88be230f-b765-4f0e-b165-bc69c8576d39', 1790920895979, 1790946051139, NULL, 1),
('1b41efa3-6545-4d9c-b5a7-bff9a3721478', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '71367108-8d71-4651-8769-4aa274dbad0d', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 250000, 1790377200000, '', 'bc182ce0-cdff-4639-b1b2-24886664d59f', 1790507931606, 1790507931693, NULL, 1),
('24615f12-c865-4dc8-8998-5fed8c30971b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '584e9697-f678-4247-9440-71c63ec9ab61', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 5000, 1789375065166, '', 'b11a0d04-1e54-4b0e-bc71-c18191133f2b', 1789375075359, 1789383160431, NULL, 1),
('28ce4cf5-db90-4644-a142-81a02f2f4e61', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1b2ee384-848b-4a29-b144-a2dfabd5d1fe', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 10000, 1789407435556, '', '3b18667e-2882-4d07-9db8-ec1c09904962', 1789407444691, 1789478227856, 1789478227856, 2),
('342929f7-79f1-422a-a009-a4d27592f237', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '84c2f96d-982a-4324-a782-b6b967ede88a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 5000, 1790945580000, '', '4b64d07a-0c68-4547-8d41-19826dcd342c', 1790945615002, 1790945635139, NULL, 1),
('39c91c2f-c0dc-4384-9a74-489b53331890', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2c343910-495b-42e7-b8e6-ac4076a76b0a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 1500000, 1789478607317, '', '7a507cc9-2660-448f-ac32-331b393a7543', 1789478619167, 1789479114172, NULL, 1),
('3ef11f69-7200-4d23-85ef-64f8b1257fb0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '92db4805-3e2b-4343-8118-577cc7ce4248', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 300000, 1774998000000, '', '882151bb-9d60-4418-931b-41ed2c14c3f3', 1786825448501, 1786825448501, NULL, 1),
('83b48634-2b54-4179-b341-4b06275e7d5c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '84c2f96d-982a-4324-a782-b6b967ede88a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 30000, 1790377200000, '', '91c0f2bb-3d1d-4fb7-9f3e-ec97d01aa39b', 1790507002338, 1790507002424, NULL, 1),
('8c212d99-ff7e-4e39-a3c4-e7754199220e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'a44c2754-8bbf-4364-a5e0-13d89cb9c21b', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 50000, 1790669580000, '', '5e68a759-9180-4440-b2e5-9ffadcf86c38', 1790673206940, 1790673380489, NULL, 1),
('9b6b9619-4f6a-4060-9b0a-a0a025ddd8eb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '84c2f96d-982a-4324-a782-b6b967ede88a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 15000, 1790625600000, '', 'eb200034-9c6a-43b2-9039-f019651ca6b3', 1790673021838, 1790673380487, NULL, 1),
('b1a7f80d-9f31-49c9-9f49-824a1105550b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '109eecad-125e-4f2c-be4c-9ae030a972e3', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 100000, 1788298587005, '', 'e51c5b02-0840-48d0-b41b-fb67b4cc0661', 1788298607169, 1788298607169, NULL, 1),
('b1bafb34-0e85-4172-b141-0040b4d16db5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6107c50e-7b23-490f-aecb-692ad6c439ed', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 25000, 1790752860000, '', '99d9ba1d-ddd9-40f9-8bc6-1947ab779dec', 1790803297550, 1790803298355, NULL, 1),
('b99156d0-71ca-4ff8-ba3d-07973a82db07', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'e0a11624-40db-4b51-a85d-1067884d13c7', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 400000, 1790204400000, '', '70b665c9-e128-49e2-be97-f7dc5843d8d6', 1790243756091, 1790243758913, NULL, 1),
('c04e6205-362d-4950-b365-63e6325319ed', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '1b2ee384-848b-4a29-b144-a2dfabd5d1fe', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 5000, 1789407034843, '', 'f06e33bb-d00e-425a-9bb7-cdaf20dbb5b0', 1789407049955, 1789478227847, 1789478227847, 2),
('d0407f1b-9f79-4536-8c8b-c510dd767cc9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6107c50e-7b23-490f-aecb-692ad6c439ed', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 125000, 1790929560000, '', 'ddaf50b0-9b9e-468c-9892-8311487ebf82', 1790933220265, 1790933220591, NULL, 1),
('ddeab02a-0ecc-4220-a676-20e4dca12d19', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'f416f1a2-f955-4c83-830b-4f7d67e9d73e', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 100000, 1790377200000, '', 'e301d438-b99b-4961-9bf7-bcd681ab38f6', 1790507035105, 1790507035176, NULL, 1),
('f3926ba3-d2de-4308-8c81-d1c6518f9ff8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6107c50e-7b23-490f-aecb-692ad6c439ed', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 25000, 1790796600000, '', '08166453-7cb4-4145-9d22-138e4b3c0306', 1790803332269, 1790803333081, NULL, 1),
('f620533b-e98c-436e-b008-785fd4d4d3af', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6d60302d-a0c9-4752-acaa-3990dad51f0c', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 200000, 1789513200000, '', '72e13c34-9bcf-44a7-820a-3d84ef11f122', 1789725247919, 1789725248399, NULL, 1),
('feb9bfb7-8521-4018-94a2-f3cbcb49add5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '6107c50e-7b23-490f-aecb-692ad6c439ed', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 30000, 1790937180000, '', 'b2e0baa2-f7b4-461f-9d88-51be64c2fa96', 1791023645202, 1791023711331, NULL, 1);

-- --------------------------------------------------------

--
-- Structure de la table `persons`
--

CREATE TABLE `persons` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `persons`
--

INSERT INTO `persons` (`id`, `user_id`, `name`, `phone`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('03c0c6af-4ca3-426c-9d38-0935f430c0e4', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'banque', NULL, 1790399606561, 1790418358585, NULL, 1),
('097204d1-2212-4c27-ae35-46290684f831', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Dille', '90 63 71 32', 1786741617063, 1786741617063, NULL, 1),
('0e912827-caa6-41bf-af06-8c654f14a562', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Kader Qwiper', '92 27 36 94', 1789478453093, 1789479114030, NULL, 1),
('32ab7d28-65eb-4d41-b374-8d04ce767f67', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Ridouane', '+227 80 92 94 40', 1786742085593, 1786742085593, NULL, 1),
('33c2e440-3c9a-4552-897d-40ec35402f9d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'LI', '+227 89 45 33 60', 1790241859008, 1790242787102, NULL, 1),
('3fcfa015-9351-4669-832c-bef003b2d277', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Boube', '98256438', 1790334665164, 1790334665935, NULL, 1),
('848b9cb6-434a-41cb-8b6e-cc5a877fd227', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Maman', NULL, 1786740769607, 1786740769607, NULL, 1),
('862cadb4-1f9b-442a-94fa-e38d14419d96', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'La Dame qui vends de la nourriture', NULL, 1789117370192, 1789117578468, NULL, 1),
('9656c1b6-af21-4935-9497-00055b0c72d2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Omar Qwiper', '+22797927042', 1788214750233, 1788214750233, NULL, 1),
('9cd5168e-c908-49e0-9208-40b2257a89b0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Abdallah Qwiper', '98 05 78 52', 1789117158845, 1789117165125, NULL, 1),
('bde248cb-42f6-44bb-a9d8-11cd928d05a9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Alazi', '92379264', 1788558334863, 1788558335806, NULL, 1),
('d1ae9468-48dc-4d8a-a5f5-e9a48c35f0a5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Ramatou GS', '97459852', 1790401294979, 1790401853984, NULL, 1),
('dd41f65d-b14e-47ac-b518-0f0749d6c944', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Abdoul Karim (MNF)', '+22797272185', 1790507680350, 1790507680505, NULL, 1),
('f5894388-ffb3-4bb0-9614-5cbc028400be', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Abdoul Karim GF', '92084349', 1789934168020, 1789934168771, NULL, 1);

-- --------------------------------------------------------

--
-- Structure de la table `receipts`
--

CREATE TABLE `receipts` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_path` varchar(500) NOT NULL,
  `received_at` bigint(20) NOT NULL,
  `file_size` bigint(20) NOT NULL,
  `mime_type` varchar(127) NOT NULL,
  `source_app` varchar(191) DEFAULT NULL,
  `source_name` varchar(191) DEFAULT NULL,
  `amount_minor` bigint(20) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `recurring_transactions`
--

CREATE TABLE `recurring_transactions` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `type` varchar(16) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `account_id` char(36) NOT NULL,
  `category_id` char(36) DEFAULT NULL,
  `description` text NOT NULL,
  `payment_method` varchar(32) DEFAULT NULL,
  `start_date` bigint(20) NOT NULL,
  `end_date` bigint(20) DEFAULT NULL,
  `frequency` varchar(32) NOT NULL,
  `next_execution_date` bigint(20) NOT NULL,
  `is_active` tinyint(1) NOT NULL,
  `trigger_hour` tinyint(4) NOT NULL,
  `trigger_minute` tinyint(4) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `recurring_transactions`
--

INSERT INTO `recurring_transactions` (`id`, `user_id`, `type`, `amount`, `account_id`, `category_id`, `description`, `payment_method`, `start_date`, `end_date`, `frequency`, `next_execution_date`, `is_active`, `trigger_hour`, `trigger_minute`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 100000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '8e4f9cf0-c631-489d-be3a-757f533fb3f1', '', 'CASH', 1788473028371, NULL, 'DAILY', 1788562800000, 1, 23, 20, 1788473091344, 1788515862729, 1788515862729, 4),
('3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 25000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'Dîner', 'CASH', 1788646264275, NULL, 'DAILY', 1789686000000, 0, 19, 0, 1788646328315, 1789721059325, NULL, 16),
('4024059d-899a-4c93-b9b4-6dfc6ebd6fe3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 20000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '8e4f9cf0-c631-489d-be3a-757f533fb3f1', 'test1', NULL, 1787654580589, NULL, 'MONTHLY', 1790290800000, 0, 8, 0, 1787654622125, 1788128038972, NULL, 2),
('44e4774b-59bc-401e-ad25-fb85a3fa2f2f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 20000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '0d8805a3-1d31-4a01-bf39-0807657aa848', 'Test', 'CASH', 1788539563167, NULL, 'DAILY', 1788649200000, 1, 17, 35, 1788539615440, 1788646401905, 1788646401905, 4),
('559a86b8-96d3-4380-bae4-8f51be9ba31f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 25000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', '', 'CASH', 1788390000000, NULL, 'DAILY', 1788562800000, 1, 20, 0, 1788454466444, 1788515862720, 1788515862720, 4),
('64a9dada-f4ec-431f-8150-c1fd099c5787', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 30000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '999243ea-2013-4d43-8863-f876c3dbfd2e', '', 'CASH', 1787060698015, NULL, 'ONCE', 1787060698015, 0, 8, 0, 1787060748923, 1787060757021, NULL, 1),
('9cbacfd5-cbe3-4e02-98b4-e8974a281416', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 30000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'Petit-déjeuner', 'CASH', 1788644731835, NULL, 'DAILY', 1789772400000, 0, 9, 0, 1788644885916, 1789721057972, NULL, 21),
('b160849e-d7b6-4769-af5e-f6d5fad5a2e3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 10000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '0d8805a3-1d31-4a01-bf39-0807657aa848', '', NULL, 1788538247591, NULL, 'MONTHLY', 1791068400000, 1, 17, 15, 1788538315334, 1788539543909, 1788539543909, 3),
('c0737d1a-1124-400a-bab1-6d76ba836cbb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 30000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', '', NULL, 1787785200000, NULL, 'MONTHLY', 1790463600000, 0, 9, 0, 1787846855265, 1788128040474, NULL, 3),
('ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'EXPENSE', 25000, 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'Déjeuner', 'CASH', 1788644932488, NULL, 'DAILY', 1789686000000, 0, 14, 0, 1788646259394, 1789721055750, NULL, 16);

-- --------------------------------------------------------

--
-- Structure de la table `recurring_transaction_occurrences`
--

CREATE TABLE `recurring_transaction_occurrences` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `recurring_transaction_id` char(36) NOT NULL,
  `scheduled_date` bigint(20) NOT NULL,
  `status` varchar(32) NOT NULL,
  `transaction_id` char(36) DEFAULT NULL,
  `processed_at` bigint(20) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `recurring_transaction_occurrences`
--

INSERT INTO `recurring_transaction_occurrences` (`id`, `user_id`, `recurring_transaction_id`, `scheduled_date`, `status`, `transaction_id`, `processed_at`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('046f5798-a0b9-4755-9ca7-e429e3f2f726', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789426800000, 'REJECTED', NULL, 1789721035506, 1789511979093, 1789721042860, NULL, 2),
('0a84ec78-c6de-4b3c-8af1-d9b377b727dc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789599600000, 'REJECTED', NULL, 1789721051252, 1789685227754, 1789721051745, NULL, 2),
('0d40c634-66c6-47d4-bdcf-7251d6dc9f79', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1788644932488, 'REJECTED', NULL, 1788646334815, 1788646264546, 1788646401612, NULL, 2),
('0ec2ee2e-c328-41fa-879a-c0aaf55cdd5e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789513200000, 'REJECTED', NULL, 1789721052934, 1789685227754, 1789721053356, NULL, 2),
('1042fcd0-49d8-422b-ae7a-6f7903a8b1c5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789340400000, 'REJECTED', NULL, 1789480308318, 1789419760735, 1789480308380, NULL, 2),
('10b90172-2c68-4d2a-9843-803102223f34', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789513200000, 'REJECTED', NULL, 1789721040635, 1789685227754, 1789721042875, NULL, 2),
('18d0ee74-62ee-4731-a72f-0df853e1de1b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1788735600000, 'ACCEPTED', '394ecac4-d6ab-4955-9bfb-60d3e52d5b3a', 1788909938048, 1788804110161, 1788909943965, NULL, 2),
('1aea517c-05f2-4c97-a5aa-f7445cb4f63b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1788735600000, 'ACCEPTED', 'f2cc4f28-5541-41f2-bcc1-2f5f45552020', 1788789480406, 1788786290389, 1788789484167, NULL, 2),
('1da0b56c-1f7f-472b-adb8-71b2b85da042', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '44e4774b-59bc-401e-ad25-fb85a3fa2f2f', 1788539563167, 'ACCEPTED', 'df62e1ae-8803-44be-b28f-5f89772c634b', 1788555506241, 1788539765396, 1788646401567, 1788646401567, 3),
('22474ad2-a3fd-4e15-a2c3-65ca4ffdd4a5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789081200000, 'ACCEPTED', '0b5cbee0-5a3a-41a6-97fa-1c8a67c8c569', 1789132634283, 1789131649167, 1789132666333, NULL, 2),
('227884af-aee1-4138-aa10-fb51e385f4cf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789081200000, 'ACCEPTED', 'e2eeb172-63b7-42b4-ba60-d22b3b9d9d26', 1789478246438, 1789161499332, 1789478246685, NULL, 2),
('2554237e-22ac-4670-b7aa-982ba7f59f25', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789599600000, 'REJECTED', NULL, 1789721047252, 1789685227754, 1789721050584, NULL, 2),
('30ceef78-b4f1-4aaf-82ec-fdc1bab35ca5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1788908400000, 'REJECTED', NULL, 1788959285942, 1788958812482, 1788959286785, NULL, 2),
('331950ae-9b24-4244-befb-7483ebb8c73c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789167600000, 'ACCEPTED', '42e9dd67-d44d-46f6-b6d3-bafe84d3b154', 1789406015969, 1789218104670, 1789406017521, NULL, 2),
('3b4aacb8-78b7-43cf-a1fe-de25ec728677', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3916d5a6-6e1c-4358-b2a6-593bf7f7103c', 1788476400000, 'REJECTED', NULL, 1788512522069, 1788512458704, 1788539543804, 1788539543804, 3),
('42d72f76-70c8-4309-aff1-fc678e587620', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '64a9dada-f4ec-431f-8150-c1fd099c5787', 1787060698015, 'REJECTED', NULL, 1787060807871, 1787060757021, 1787060757021, NULL, 1),
('442e859d-3f09-479a-8818-af1770ff1373', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789599600000, 'REJECTED', NULL, 1789721044183, 1789685227754, 1789721044567, NULL, 2),
('4a325c4b-98ab-4ccc-b79c-ad1f9bbf0bd1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1788649200000, 'ACCEPTED', 'b0f70324-7df0-46f6-bee8-7be0f559238b', 1788772058620, 1788717822299, 1788772058466, NULL, 2),
('4c1ad811-61be-4af4-bc47-24c4e7c56bcd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7', 1788476400000, 'ACCEPTED', '5b238f86-1aa6-4f7b-a7b0-adafb9ac6cc8', 1788511392131, 1788490924942, 1788515862267, 1788515862267, 3),
('53f8dcd8-a91a-44cc-b833-9087e7db4902', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789081200000, 'REJECTED', NULL, 1789135698671, 1789121989230, 1789135699691, NULL, 2),
('54619eba-c2b8-49eb-9b60-7ccbfe900734', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '559a86b8-96d3-4380-bae4-8f51be9ba31f', 1788390000000, 'ACCEPTED', '17dc5d9a-5756-4f03-a722-bb308bfffdb8', 1788454495899, 1788454476809, 1788515862246, 1788515862246, 3),
('5af70d0c-7df2-4116-bda0-b21887cf2264', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789167600000, 'ACCEPTED', '8271d8f5-4e72-4bdf-a406-060a45b51c73', 1789242379961, 1789236092203, 1789382164124, NULL, 2),
('5d985aa8-75ea-47cf-8372-c7e31e7a4e72', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789426800000, 'REJECTED', NULL, 1789480309551, 1789465422091, 1789480309558, NULL, 2),
('5f121f98-6d21-4682-9f52-a4a301f3e7c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1788994800000, 'ACCEPTED', '72166804-ffc3-4642-beb3-455d01ccd560', 1789032085938, 1789029576755, 1789032086241, NULL, 2),
('6434ef2d-1987-493d-b803-cc4c408801bf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'c0737d1a-1124-400a-bab1-6d76ba836cbb', 1787785200000, 'ACCEPTED', '211f853c-5368-4390-8337-439a25b81384', 1787902710131, 1787866947868, 1787902710131, NULL, 2),
('650aed4c-9d9b-4256-aa9a-380623bd2548', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789686000000, 'REJECTED', NULL, 1789721045801, 1789719796451, 1789721046224, NULL, 2),
('674bb55a-c74b-4641-96a5-99b835a584d8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1788908400000, 'ACCEPTED', 'bce58b7f-1e42-49fa-9078-48cf261d07d5', 1788979872017, 1788977102088, 1788979872516, NULL, 2),
('70190c11-2330-4ac1-8c00-6de0314c8d6c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1788646264275, 'MODIFIED', '6d3aabaf-259f-4234-b24e-9a5c57320a78', 1788646379313, 1788646333374, 1788646401618, NULL, 2),
('7955637c-82c5-41a8-b970-86ca38a19dcd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1788735600000, 'ACCEPTED', '2d265038-f1b5-40fe-8134-8ece8bea1037', 1788772065616, 1788769761022, 1788772065520, NULL, 2),
('83ef9d9e-59a1-435a-b8c3-54ec8ffe20be', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '4024059d-899a-4c93-b9b4-6dfc6ebd6fe3', 1787654580589, 'REJECTED', NULL, 1787654695066, 1787654627153, 0, NULL, 1),
('8f4700b2-1350-4df2-b4de-d84a5b6bd334', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789254000000, 'REJECTED', NULL, 1789401429896, 1789286547832, 1789401430092, NULL, 2),
('9a3ffe39-0dd9-4107-b069-a8d768f82131', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1789254000000, 'ACCEPTED', '19fff101-04e8-4966-af0e-7fa0c96b5798', 1789325890312, 1789324049840, 1789382164154, NULL, 2),
('a800ec02-7c00-47b9-993b-2763cda2e494', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '559a86b8-96d3-4380-bae4-8f51be9ba31f', 1788476400000, 'ACCEPTED', 'ec6398fd-2f1b-425e-a2c9-45941500301d', 1788511383829, 1788490924942, 1788515862251, 1788515862251, 3),
('ac5ed017-1522-4165-bca2-19720445c58d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1788644731835, 'MODIFIED', 'f70356ba-9d3e-4ad6-8c66-b06455c291ab', 1788644924129, 1788644891049, 1788646401595, NULL, 2),
('b20cbf28-b54e-4435-8f7f-738c0766ae24', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789340400000, 'ACCEPTED', '9552a14d-6f90-4fdf-936b-d57e14d95990', 1789375042924, 1789372847012, 1789393433998, NULL, 2),
('bf465406-f3d0-4704-8983-bde2a77d3031', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', 1788994800000, 'ACCEPTED', '27c2a71e-149a-4b9a-965a-37c3d584c743', 1789067930274, 1789063227241, 1789069459667, NULL, 2),
('bf48763e-78b0-4a62-a6da-ad1d55c6a16e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789426800000, 'ACCEPTED', 'a48f2e01-a9a7-4a8f-8298-4eb2b106f307', 1789478268642, 1789477270292, 1789478268811, NULL, 2),
('bf89dba1-f8b2-4c60-8655-d53f70081ec6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789254000000, 'REJECTED', NULL, 1789401410227, 1789304552553, 1789401410448, NULL, 2),
('c49485a9-224d-44a8-a4f9-08fe31fe8e10', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789513200000, 'REJECTED', NULL, 1789721042285, 1789685227754, 1789721043852, NULL, 2),
('c6a7efb4-375b-45ed-a31c-4ce881753e01', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7', 1788473028371, 'MODIFIED', 'b11f7661-2de0-447a-918d-63b26f852db3', 1788473114235, 1788473103089, 1788515862262, 1788515862262, 3),
('cb8b387e-cdc8-484f-875e-eb04927ab8fa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1788822000000, 'ACCEPTED', '56e44f7a-0034-4f67-8b1c-2226d1bd5433', 1788909939046, 1788859926442, 1788909943978, NULL, 2),
('cd7aa7e8-6075-4475-a5dc-3a0028a2fe3e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1788649200000, 'MODIFIED', '8fed17e3-b135-48ca-a8ee-4b08055176e0', 1788772034065, 1788681641165, 1788772033930, NULL, 2),
('d6292f40-a05d-4647-9485-1cb86a8aa60b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1788649200000, 'REJECTED', NULL, 1788772051376, 1788699623651, 1788772051730, NULL, 2),
('d96dbd4f-b4e9-4517-8c96-35037095f96f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '44e4774b-59bc-401e-ad25-fb85a3fa2f2f', 1788562800000, 'REJECTED', NULL, 1788644724553, 1788626704080, 1788646401573, 1788646401573, 3),
('ef667a1b-de33-4d8c-99eb-e9c89efccf51', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'b160849e-d7b6-4769-af5e-f6d5fad5a2e3', 1788538247591, 'PENDING', NULL, NULL, 1788538580895, 1788539543818, 1788539543818, 2),
('f5168674-c954-4741-8603-3a0a486dc8e3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1789340400000, 'ACCEPTED', '91e81bb8-38b9-4e4b-a2f3-914c7d6f004a', 1789401432566, 1789393444081, 1789401432747, NULL, 2),
('f5993262-f415-48b2-9b1c-ce8be0c31665', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1788822000000, 'ACCEPTED', '08fc0dfd-2922-4aec-b215-c518cad4e78d', 1788909940052, 1788872408582, 1788909943995, NULL, 2),
('fa05cba0-973c-4396-958c-9ae52cb35189', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', 1788994800000, 'ACCEPTED', '685c166b-46f8-4147-91d0-ee4f7a53c554', 1789049631693, 1789046183218, 1789049632208, NULL, 2),
('fa6cdf30-012b-49a9-91b6-c0480a096686', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1789167600000, 'ACCEPTED', 'a5664b92-b636-422d-b45b-e7b3848a7ed0', 1789214399428, 1789201128757, 1789214638773, NULL, 2),
('fb326d6e-7e3c-4c10-a421-7747641bbb21', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', 1788908400000, 'ACCEPTED', '2a7b1f46-8851-4a56-98e6-df47624e8920', 1788941284435, 1788940956836, 1788941284821, NULL, 2);

-- --------------------------------------------------------

--
-- Structure de la table `savings_goals`
--

CREATE TABLE `savings_goals` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `target_amount` bigint(20) NOT NULL,
  `current_amount` bigint(20) NOT NULL,
  `currency_code` char(3) NOT NULL,
  `deadline` bigint(20) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `savings_goals`
--

INSERT INTO `savings_goals` (`id`, `user_id`, `name`, `target_amount`, `current_amount`, `currency_code`, `deadline`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('29e1d94f-00f2-4cbb-b7ae-e4ae06b00289', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Mariage', 100000000, 20000000, 'XOF', NULL, 1790288587785, 1790311207028, 1790311207028, 2);

-- --------------------------------------------------------

--
-- Structure de la table `sync_conflicts`
--

CREATE TABLE `sync_conflicts` (
  `id` bigint(20) NOT NULL,
  `user_id` char(36) NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` char(36) NOT NULL,
  `losing_payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`losing_payload`)),
  `winning_payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`winning_payload`)),
  `created_at` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `sync_conflicts`
--

INSERT INTO `sync_conflicts` (`id`, `user_id`, `entity_type`, `entity_id`, `losing_payload`, `winning_payload`, `created_at`) VALUES
(1, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'user_preferences', '6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6', '{\"id\": \"6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6\", \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 8, \"created_at\": 1787781788861, \"deleted_at\": null, \"theme_mode\": \"DARK\", \"updated_at\": 1788126833512, \"currency_code\": \"XOF\"}', '{\"id\": \"6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6\", \"createdAt\": 1787781788861, \"themeMode\": \"DARK\", \"updatedAt\": 1788126845560, \"baseVersion\": 7, \"currencyCode\": \"XOF\"}', 1788126852463),
(2, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'transactions', '18523a54-ca61-4bef-b456-d7ccb96d8ce9', '{\"id\": \"18523a54-ca61-4bef-b456-d7ccb96d8ce9\", \"date\": 1788267600000, \"type\": \"TRANSFER\", \"amount\": 4660000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 2, \"fee_type\": null, \"latitude\": null, \"longitude\": null, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788297997747, \"deleted_at\": null, \"updated_at\": 1788348529195, \"category_id\": null, \"description\": \"Espèces → MyNita\", \"payment_method\": null, \"fee_transaction_id\": null, \"transfer_account_id\": \"44c112d3-4787-4ad6-b9c5-93649aaff584\"}', '{\"id\": \"18523a54-ca61-4bef-b456-d7ccb96d8ce9\", \"date\": 1788267600000, \"type\": \"TRANSFER\", \"amount\": 4360000, \"feeType\": null, \"latitude\": null, \"createdAt\": 1788297997747, \"longitude\": null, \"updatedAt\": 1788343341474, \"baseVersion\": 1, \"description\": \"Espèces → MyNita\", \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": null, \"categorySyncId\": null, \"feeTransactionSyncId\": null, \"transferAccountSyncId\": \"44c112d3-4787-4ad6-b9c5-93649aaff584\"}', 1788348529200),
(3, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transaction_occurrences', 'a800ec02-7c00-47b9-993b-2763cda2e494', '{\"id\": \"a800ec02-7c00-47b9-993b-2763cda2e494\", \"status\": \"ACCEPTED\", \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 2, \"created_at\": 1788490924942, \"deleted_at\": null, \"updated_at\": 1788515862237, \"processed_at\": 1788511383829, \"scheduled_date\": 1788476400000, \"transaction_id\": \"ec6398fd-2f1b-425e-a2c9-45941500301d\", \"recurring_transaction_id\": \"559a86b8-96d3-4380-bae4-8f51be9ba31f\"}', '{\"id\": \"a800ec02-7c00-47b9-993b-2763cda2e494\", \"status\": \"ACCEPTED\", \"createdAt\": 1788490924942, \"updatedAt\": 1788511543422, \"baseVersion\": 1, \"processedAt\": 1788511383829, \"scheduledDate\": 1788476400000, \"transactionSyncId\": \"ec6398fd-2f1b-425e-a2c9-45941500301d\", \"recurringTransactionSyncId\": \"559a86b8-96d3-4380-bae4-8f51be9ba31f\"}', 1788515862251),
(4, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transaction_occurrences', '4c1ad811-61be-4af4-bc47-24c4e7c56bcd', '{\"id\": \"4c1ad811-61be-4af4-bc47-24c4e7c56bcd\", \"status\": \"ACCEPTED\", \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 2, \"created_at\": 1788490924942, \"deleted_at\": null, \"updated_at\": 1788515862241, \"processed_at\": 1788511392131, \"scheduled_date\": 1788476400000, \"transaction_id\": \"5b238f86-1aa6-4f7b-a7b0-adafb9ac6cc8\", \"recurring_transaction_id\": \"2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7\"}', '{\"id\": \"4c1ad811-61be-4af4-bc47-24c4e7c56bcd\", \"status\": \"ACCEPTED\", \"createdAt\": 1788490924942, \"updatedAt\": 1788511549994, \"baseVersion\": 1, \"processedAt\": 1788511392131, \"scheduledDate\": 1788476400000, \"transactionSyncId\": \"5b238f86-1aa6-4f7b-a7b0-adafb9ac6cc8\", \"recurringTransactionSyncId\": \"2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7\"}', 1788515862267),
(5, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '559a86b8-96d3-4380-bae4-8f51be9ba31f', '{\"id\": \"559a86b8-96d3-4380-bae4-8f51be9ba31f\", \"type\": \"EXPENSE\", \"amount\": 25000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 3, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788454466444, \"deleted_at\": null, \"start_date\": 1788390000000, \"updated_at\": 1788515862702, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"\", \"trigger_hour\": 20, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788562800000}', '{\"id\": \"559a86b8-96d3-4380-bae4-8f51be9ba31f\", \"type\": \"EXPENSE\", \"amount\": 25000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788454466444, \"frequency\": \"DAILY\", \"startDate\": 1788390000000, \"updatedAt\": 1788511543422, \"baseVersion\": 2, \"description\": \"\", \"triggerHour\": 20, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788562800000}', 1788515862720),
(6, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7', '{\"id\": \"2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7\", \"type\": \"EXPENSE\", \"amount\": 100000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 3, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788473091344, \"deleted_at\": null, \"start_date\": 1788473028371, \"updated_at\": 1788515862713, \"category_id\": \"8e4f9cf0-c631-489d-be3a-757f533fb3f1\", \"description\": \"\", \"trigger_hour\": 23, \"payment_method\": \"CASH\", \"trigger_minute\": 20, \"next_execution_date\": 1788562800000}', '{\"id\": \"2c87f5e7-c0a7-47f5-9cc5-45ade2e5c9d7\", \"type\": \"EXPENSE\", \"amount\": 100000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788473091344, \"frequency\": \"DAILY\", \"startDate\": 1788473028371, \"updatedAt\": 1788511549994, \"baseVersion\": 2, \"description\": \"\", \"triggerHour\": 23, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 20, \"categorySyncId\": \"8e4f9cf0-c631-489d-be3a-757f533fb3f1\", \"nextExecutionDate\": 1788562800000}', 1788515862729),
(7, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transaction_occurrences', 'd96dbd4f-b4e9-4517-8c96-35037095f96f', '{\"id\": \"d96dbd4f-b4e9-4517-8c96-35037095f96f\", \"status\": \"REJECTED\", \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 2, \"created_at\": 1788626704080, \"deleted_at\": null, \"updated_at\": 1788646401523, \"processed_at\": 1788644724553, \"scheduled_date\": 1788562800000, \"transaction_id\": null, \"recurring_transaction_id\": \"44e4774b-59bc-401e-ad25-fb85a3fa2f2f\"}', '{\"id\": \"d96dbd4f-b4e9-4517-8c96-35037095f96f\", \"status\": \"REJECTED\", \"createdAt\": 1788626704080, \"updatedAt\": 1788644728437, \"baseVersion\": 1, \"processedAt\": 1788644724553, \"scheduledDate\": 1788562800000, \"transactionSyncId\": null, \"recurringTransactionSyncId\": \"44e4774b-59bc-401e-ad25-fb85a3fa2f2f\"}', 1788646401573),
(8, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 2, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1788646401921, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 8, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788649200000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1788644899555, \"baseVersion\": 1, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788649200000}', 1788646401927),
(9, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 5, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1788769761258, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 9, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788822000000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1788768277262, \"baseVersion\": 4, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788822000000}', 1788772570664),
(10, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', '{\"id\": \"3b7e4b2d-7aa8-4641-aac0-cb10fb56e126\", \"type\": \"EXPENSE\", \"amount\": 25000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 4, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788646328315, \"deleted_at\": null, \"start_date\": 1788646264275, \"updated_at\": 1788804110229, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Dîner\", \"trigger_hour\": 19, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788822000000}', '{\"id\": \"3b7e4b2d-7aa8-4641-aac0-cb10fb56e126\", \"type\": \"EXPENSE\", \"amount\": 25000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788646328315, \"frequency\": \"DAILY\", \"startDate\": 1788646264275, \"updatedAt\": 1788804001173, \"baseVersion\": 3, \"description\": \"Dîner\", \"triggerHour\": 19, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788822000000}', 1788804220380),
(11, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 7, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1788859927312, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 9, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788908400000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1788855754173, \"baseVersion\": 6, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788908400000}', 1788859957767),
(12, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', '{\"id\": \"ec8f8fef-c3b7-4aa4-94ff-d17163d1751e\", \"type\": \"EXPENSE\", \"amount\": 25000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 5, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788646259394, \"deleted_at\": null, \"start_date\": 1788644932488, \"updated_at\": 1788872409828, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Déjeuner\", \"trigger_hour\": 14, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788908400000}', '{\"id\": \"ec8f8fef-c3b7-4aa4-94ff-d17163d1751e\", \"type\": \"EXPENSE\", \"amount\": 25000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788646259394, \"frequency\": \"DAILY\", \"startDate\": 1788644932488, \"updatedAt\": 1788873458398, \"baseVersion\": 4, \"description\": \"Déjeuner\", \"triggerHour\": 14, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788908400000}', 1788873560345),
(13, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', 'ec8f8fef-c3b7-4aa4-94ff-d17163d1751e', '{\"id\": \"ec8f8fef-c3b7-4aa4-94ff-d17163d1751e\", \"type\": \"EXPENSE\", \"amount\": 25000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 7, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788646259394, \"deleted_at\": null, \"start_date\": 1788644932488, \"updated_at\": 1788958813502, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Déjeuner\", \"trigger_hour\": 14, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1788994800000}', '{\"id\": \"ec8f8fef-c3b7-4aa4-94ff-d17163d1751e\", \"type\": \"EXPENSE\", \"amount\": 25000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788646259394, \"frequency\": \"DAILY\", \"startDate\": 1788644932488, \"updatedAt\": 1788959093398, \"baseVersion\": 6, \"description\": \"Déjeuner\", \"triggerHour\": 14, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1788994800000}', 1788959097554),
(14, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'user_preferences', '6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6', '{\"id\": \"6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6\", \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 12, \"created_at\": 1787781788861, \"deleted_at\": null, \"theme_mode\": \"LIGHT\", \"updated_at\": 1788978752284, \"currency_code\": \"XOF\"}', '{\"id\": \"6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6\", \"createdAt\": 1787781788861, \"themeMode\": \"SYSTEM\", \"updatedAt\": 1788974430368, \"baseVersion\": 11, \"currencyCode\": \"XOF\"}', 1788978752329),
(15, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 10, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1789029576671, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 9, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1789081200000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1789028547960, \"baseVersion\": 9, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1789081200000}', 1789030216779),
(16, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 12, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1789121988732, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 9, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1789167600000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1789113623902, \"baseVersion\": 11, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1789167600000}', 1789135312368),
(17, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', '{\"id\": \"3b7e4b2d-7aa8-4641-aac0-cb10fb56e126\", \"type\": \"EXPENSE\", \"amount\": 25000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 9, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788646328315, \"deleted_at\": null, \"start_date\": 1788646264275, \"updated_at\": 1789161500097, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Dîner\", \"trigger_hour\": 19, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1789167600000}', '{\"id\": \"3b7e4b2d-7aa8-4641-aac0-cb10fb56e126\", \"type\": \"EXPENSE\", \"amount\": 25000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788646328315, \"frequency\": \"DAILY\", \"startDate\": 1788646264275, \"updatedAt\": 1789149883910, \"baseVersion\": 8, \"description\": \"Dîner\", \"triggerHour\": 19, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1789167600000}', 1789214412947),
(18, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 14, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1789388326517, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 9, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1789426800000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1789372847012, \"baseVersion\": 13, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1789426800000}', 1789393433831),
(19, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '3b7e4b2d-7aa8-4641-aac0-cb10fb56e126', '{\"id\": \"3b7e4b2d-7aa8-4641-aac0-cb10fb56e126\", \"type\": \"EXPENSE\", \"amount\": 25000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 12, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788646328315, \"deleted_at\": null, \"start_date\": 1788646264275, \"updated_at\": 1789419762104, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Dîner\", \"trigger_hour\": 19, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1789426800000}', '{\"id\": \"3b7e4b2d-7aa8-4641-aac0-cb10fb56e126\", \"type\": \"EXPENSE\", \"amount\": 25000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788646328315, \"frequency\": \"DAILY\", \"startDate\": 1788646264275, \"updatedAt\": 1789409028955, \"baseVersion\": 11, \"description\": \"Dîner\", \"triggerHour\": 19, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1789426800000}', 1789419897876),
(20, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'loans', '1b2ee384-848b-4a29-b144-a2dfabd5d1fe', '{\"id\": \"1b2ee384-848b-4a29-b144-a2dfabd5d1fe\", \"type\": \"BORROWED\", \"amount\": 2000000, \"reason\": \"OTHER\", \"status\": \"OVERDUE\", \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 4, \"due_date\": 1788130800000, \"person_id\": \"848b9cb6-434a-41cb-8b6e-cc5a877fd227\", \"account_id\": \"44c112d3-4787-4ad6-b9c5-93649aaff584\", \"created_at\": 1786740833775, \"deleted_at\": null, \"start_date\": 1786740734105, \"updated_at\": 1789478227747, \"description\": \"\", \"amount_repaid\": 10000, \"repayment_mode\": \"SINGLE\", \"transaction_id\": \"694a2c87-f68b-4535-8438-287bfecbb655\", \"remaining_amount\": 1990000, \"reason_custom_text\": null}', '{\"id\": \"1b2ee384-848b-4a29-b144-a2dfabd5d1fe\", \"type\": \"BORROWED\", \"amount\": 2000000, \"reason\": \"OTHER\", \"status\": \"OVERDUE\", \"dueDate\": 1788130800000, \"createdAt\": 1786740833775, \"startDate\": 1786740734105, \"updatedAt\": 1789478185601, \"baseVersion\": 3, \"description\": \"\", \"amountRepaid\": 0, \"personSyncId\": \"848b9cb6-434a-41cb-8b6e-cc5a877fd227\", \"accountSyncId\": \"44c112d3-4787-4ad6-b9c5-93649aaff584\", \"repaymentMode\": \"SINGLE\", \"remainingAmount\": 2000000, \"reasonCustomText\": null, \"transactionSyncId\": \"694a2c87-f68b-4535-8438-287bfecbb655\"}', 1789478227759),
(21, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"user_id\": \"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\", \"version\": 16, \"end_date\": null, \"frequency\": \"DAILY\", \"is_active\": 1, \"account_id\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"created_at\": 1788644885916, \"deleted_at\": null, \"start_date\": 1788644731835, \"updated_at\": 1789465421831, \"category_id\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"description\": \"Petit-déjeuner\", \"trigger_hour\": 9, \"payment_method\": \"CASH\", \"trigger_minute\": 0, \"next_execution_date\": 1789513200000}', '{\"id\": \"9cbacfd5-cbe3-4e02-98b4-e8974a281416\", \"type\": \"EXPENSE\", \"amount\": 30000, \"endDate\": null, \"isActive\": true, \"createdAt\": 1788644885916, \"frequency\": \"DAILY\", \"startDate\": 1788644731835, \"updatedAt\": 1789459270578, \"baseVersion\": 15, \"description\": \"Petit-déjeuner\", \"triggerHour\": 9, \"accountSyncId\": \"ce3ba859-001c-41d6-b65e-32c016eeb52e\", \"paymentMethod\": \"CASH\", \"triggerMinute\": 0, \"categorySyncId\": \"d82d212d-c355-4c24-8c8d-2313ab03fe0b\", \"nextExecutionDate\": 1789513200000}', 1789481047205),
(22, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'recurring_transactions', '9cbacfd5-cbe3-4e02-98b4-e8974a281416', '{\"id\":\"9cbacfd5-cbe3-4e02-98b4-e8974a281416\",\"user_id\":\"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\",\"type\":\"EXPENSE\",\"amount\":30000,\"account_id\":\"ce3ba859-001c-41d6-b65e-32c016eeb52e\",\"category_id\":\"d82d212d-c355-4c24-8c8d-2313ab03fe0b\",\"description\":\"Petit-déjeuner\",\"payment_method\":\"CASH\",\"start_date\":1788644731835,\"end_date\":null,\"frequency\":\"DAILY\",\"next_execution_date\":1789772400000,\"is_active\":1,\"trigger_hour\":9,\"trigger_minute\":0,\"created_at\":1788644885916,\"updated_at\":1789719798514,\"deleted_at\":null,\"version\":19}', '{\"id\":\"9cbacfd5-cbe3-4e02-98b4-e8974a281416\",\"baseVersion\":18,\"type\":\"EXPENSE\",\"amount\":30000,\"accountSyncId\":\"ce3ba859-001c-41d6-b65e-32c016eeb52e\",\"categorySyncId\":\"d82d212d-c355-4c24-8c8d-2313ab03fe0b\",\"description\":\"Petit-déjeuner\",\"paymentMethod\":\"CASH\",\"startDate\":1788644731835,\"endDate\":null,\"frequency\":\"DAILY\",\"nextExecutionDate\":1789772400000,\"isActive\":true,\"triggerHour\":9,\"triggerMinute\":0,\"createdAt\":1788644885916,\"updatedAt\":1789718957554}', 1789719809193),
(23, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'financial_plan_items', '9dbc2b9f-f8a5-4565-8e9d-b84d2aa03a78', '{\"id\":\"9dbc2b9f-f8a5-4565-8e9d-b84d2aa03a78\",\"user_id\":\"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\",\"plan_id\":\"4bc79ed7-d056-4cd7-9941-a667a3fdee58\",\"name\":\"Carburant\",\"amount\":1100000,\"actual_amount\":null,\"category_id\":\"2c1f02b9-05c3-4ebe-99ed-54c139279e4a\",\"description\":null,\"planned_date\":null,\"priority\":\"IMPORTANT\",\"status\":\"DONE\",\"transaction_id\":null,\"created_at\":1790191948489,\"updated_at\":1790243823076,\"deleted_at\":null,\"version\":3}', '{\"id\":\"9dbc2b9f-f8a5-4565-8e9d-b84d2aa03a78\",\"baseVersion\":2,\"planSyncId\":\"4bc79ed7-d056-4cd7-9941-a667a3fdee58\",\"name\":\"Transport\",\"amount\":1100000,\"actualAmount\":null,\"categorySyncId\":\"2c1f02b9-05c3-4ebe-99ed-54c139279e4a\",\"description\":null,\"plannedDate\":null,\"priority\":\"IMPORTANT\",\"status\":\"TO_PLAN\",\"transactionSyncId\":null,\"createdAt\":1790191948489,\"updatedAt\":1790242898045}', 1790244126520),
(24, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'financial_plan_items', 'fed6d6df-e62a-468c-b5c2-23fb728e77bb', '{\"id\":\"fed6d6df-e62a-468c-b5c2-23fb728e77bb\",\"user_id\":\"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\",\"plan_id\":\"4bc79ed7-d056-4cd7-9941-a667a3fdee58\",\"name\":\"Frais d\'electricite\",\"amount\":1000000,\"actual_amount\":null,\"category_id\":null,\"description\":null,\"planned_date\":null,\"priority\":\"OPTIONAL\",\"status\":\"DONE\",\"transaction_id\":null,\"created_at\":1790191949090,\"updated_at\":1790243838105,\"deleted_at\":null,\"version\":3}', '{\"id\":\"fed6d6df-e62a-468c-b5c2-23fb728e77bb\",\"baseVersion\":2,\"planSyncId\":\"4bc79ed7-d056-4cd7-9941-a667a3fdee58\",\"name\":\"Frais d\'electricite\",\"amount\":1000000,\"actualAmount\":null,\"categorySyncId\":\"d632080a-26ce-4dd4-a56e-f44a509a96b9\",\"description\":null,\"plannedDate\":null,\"priority\":\"OPTIONAL\",\"status\":\"TO_PLAN\",\"transactionSyncId\":null,\"createdAt\":1790191949090,\"updatedAt\":1790243079253}', 1790244126580),
(25, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'financial_plan_items', '3463e92b-675b-4bce-9a15-d4c1def099c1', '{\"id\":\"3463e92b-675b-4bce-9a15-d4c1def099c1\",\"user_id\":\"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\",\"plan_id\":\"4bc79ed7-d056-4cd7-9941-a667a3fdee58\",\"name\":\"Investissement\",\"amount\":1500000,\"actual_amount\":null,\"category_id\":\"45f98dc0-89d4-41a8-a6d0-15d5473d07dc\",\"description\":null,\"planned_date\":null,\"priority\":\"OPTIONAL\",\"status\":\"DONE\",\"transaction_id\":null,\"created_at\":1790191950944,\"updated_at\":1790244126578,\"deleted_at\":null,\"version\":4}', '{\"id\":\"3463e92b-675b-4bce-9a15-d4c1def099c1\",\"baseVersion\":3,\"planSyncId\":\"4bc79ed7-d056-4cd7-9941-a667a3fdee58\",\"name\":\"Investissement\",\"amount\":1500000,\"actualAmount\":null,\"categorySyncId\":\"420bfc42-9e1a-494a-ba85-10b52cd3b8dd\",\"description\":null,\"plannedDate\":null,\"priority\":\"OPTIONAL\",\"status\":\"DONE\",\"transactionSyncId\":null,\"createdAt\":1790191950944,\"updatedAt\":1790243114832}', 1790244126581),
(26, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"USD\",\"created_at\":1790352451846,\"updated_at\":1790352471835,\"deleted_at\":null,\"version\":2}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":1,\"themeMode\":\"SYSTEM\",\"currencyCode\":\"USD\",\"createdAt\":1790352451846,\"updatedAt\":1790352457086}', 1790352471836),
(27, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"SYSTEM\",\"currency_code\":\"USD\",\"created_at\":1790352451846,\"updated_at\":1790352471836,\"deleted_at\":null,\"version\":3}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":1,\"themeMode\":\"DARK\",\"currencyCode\":\"USD\",\"createdAt\":1790352451846,\"updatedAt\":1790352458750}', 1790352471838),
(28, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"DARK\",\"currency_code\":\"NGN\",\"created_at\":1790352451846,\"updated_at\":1790359010870,\"deleted_at\":null,\"version\":6}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":5,\"themeMode\":\"DARK\",\"currencyCode\":\"GHS\",\"createdAt\":1790352451846,\"updatedAt\":1790359004645}', 1790359010871),
(29, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'transactions', 'f5d2cbcf-234a-4e92-aa39-7f444e985a91', '{\"id\":\"f5d2cbcf-234a-4e92-aa39-7f444e985a91\",\"user_id\":\"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\",\"amount\":50000,\"type\":\"EXPENSE\",\"account_id\":\"ce3ba859-001c-41d6-b65e-32c016eeb52e\",\"transfer_account_id\":null,\"category_id\":\"425749bf-cc1e-4de1-bf48-961529658997\",\"date\":1790313900000,\"description\":\"Oriba + Œufs\",\"latitude\":null,\"longitude\":null,\"payment_method\":null,\"fee_transaction_id\":null,\"fee_type\":null,\"created_at\":1790400361734,\"updated_at\":1790401188355,\"deleted_at\":null,\"version\":2}', '{\"id\":\"f5d2cbcf-234a-4e92-aa39-7f444e985a91\",\"baseVersion\":1,\"amount\":50000,\"type\":\"EXPENSE\",\"accountSyncId\":\"ce3ba859-001c-41d6-b65e-32c016eeb52e\",\"transferAccountSyncId\":null,\"categorySyncId\":\"425749bf-cc1e-4de1-bf48-961529658997\",\"date\":1790343840000,\"description\":\"Oriba + Œufs\",\"latitude\":null,\"longitude\":null,\"paymentMethod\":null,\"feeTransactionSyncId\":null,\"feeType\":null,\"createdAt\":1790400361734,\"updatedAt\":1790400388984}', 1790401188356),
(30, '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'budgets', 'a8559082-f609-4442-b2b0-713833d4ac5a', '{\"id\":\"a8559082-f609-4442-b2b0-713833d4ac5a\",\"user_id\":\"7adaeab9-a066-11f1-9a6f-9d51bec7aafd\",\"category_id\":\"45f98dc0-89d4-41a8-a6d0-15d5473d07dc\",\"period\":\"MONTHLY\",\"limit_amount\":4900000,\"currency_code\":\"XOF\",\"start_date\":1790118000000,\"end_date\":1792882799000,\"created_at\":1790243241513,\"updated_at\":1790406977524,\"deleted_at\":null,\"version\":7}', '{\"id\":\"a8559082-f609-4442-b2b0-713833d4ac5a\",\"baseVersion\":6,\"categorySyncId\":\"45f98dc0-89d4-41a8-a6d0-15d5473d07dc\",\"period\":\"MONTHLY\",\"limitAmount\":5000000,\"currencyCode\":\"XOF\",\"startDate\":1790118000000,\"endDate\":1792882799000,\"createdAt\":1790243241513,\"updatedAt\":1790402146265}', 1790406977525),
(31, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"DARK\",\"currency_code\":\"NGN\",\"created_at\":1790352451846,\"updated_at\":1790596981892,\"deleted_at\":null,\"version\":9}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":8,\"themeMode\":\"LIGHT\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1790596970873}', 1790596981893),
(32, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"SYSTEM\",\"currency_code\":\"NGN\",\"created_at\":1790352451846,\"updated_at\":1790663309748,\"deleted_at\":null,\"version\":11}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":10,\"themeMode\":\"DARK\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1790663199609}', 1790663309749),
(33, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"DARK\",\"currency_code\":\"EUR\",\"created_at\":1790352451846,\"updated_at\":1790810150913,\"deleted_at\":null,\"version\":15}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":14,\"themeMode\":\"DARK\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1790809981590}', 1790810150914),
(34, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"SYSTEM\",\"currency_code\":\"EUR\",\"created_at\":1790352451846,\"updated_at\":1790928961693,\"deleted_at\":null,\"version\":18}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":17,\"themeMode\":\"SYSTEM\",\"currencyCode\":\"EUR\",\"createdAt\":1790352451846,\"updatedAt\":1790927371756}', 1790928961730),
(35, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'loans', '9e28b943-a942-48b9-8fad-f388f15ea2ce', '{\"id\":\"9e28b943-a942-48b9-8fad-f388f15ea2ce\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"person_id\":\"03c0c6af-4ca3-426c-9d38-0935f430c0e4\",\"account_id\":\"97d3f709-8069-4963-8c32-874009967823\",\"type\":\"LENT\",\"amount\":50000,\"amount_repaid\":6000,\"remaining_amount\":44000,\"start_date\":1790920875736,\"due_date\":1793512875736,\"reason\":\"OTHER\",\"reason_custom_text\":null,\"repayment_mode\":\"SINGLE\",\"description\":\"\",\"status\":\"ONGOING\",\"transaction_id\":\"f97c9de0-106b-45a0-9d79-2c7337adccc0\",\"gifted_amount\":0,\"gift_transaction_id\":null,\"gifted_at\":null,\"created_at\":1790920886227,\"updated_at\":1790946050225,\"deleted_at\":null,\"version\":2}', '{\"id\":\"9e28b943-a942-48b9-8fad-f388f15ea2ce\",\"baseVersion\":1,\"personSyncId\":\"03c0c6af-4ca3-426c-9d38-0935f430c0e4\",\"accountSyncId\":\"97d3f709-8069-4963-8c32-874009967823\",\"type\":\"LENT\",\"amount\":50000,\"amountRepaid\":6000,\"remainingAmount\":44000,\"startDate\":1790920875736,\"dueDate\":1793512875736,\"reason\":\"OTHER\",\"reasonCustomText\":null,\"repaymentMode\":\"SINGLE\",\"description\":\"\",\"status\":\"ONGOING\",\"transactionSyncId\":\"f97c9de0-106b-45a0-9d79-2c7337adccc0\",\"createdAt\":1790920886227,\"updatedAt\":1790920895979}', 1790946050623),
(36, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"EUR\",\"created_at\":1790352451846,\"updated_at\":1790980115926,\"deleted_at\":null,\"version\":20}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":19,\"themeMode\":\"DARK\",\"currencyCode\":\"EUR\",\"createdAt\":1790352451846,\"updatedAt\":1790979878664}', 1790980115927),
(37, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"DARK\",\"currency_code\":\"EUR\",\"created_at\":1790352451846,\"updated_at\":1790980115927,\"deleted_at\":null,\"version\":21}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":19,\"themeMode\":\"LIGHT\",\"currencyCode\":\"EUR\",\"createdAt\":1790352451846,\"updatedAt\":1790979896337}', 1790980115930),
(38, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"EUR\",\"created_at\":1790352451846,\"updated_at\":1790980115930,\"deleted_at\":null,\"version\":22}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":19,\"themeMode\":\"DARK\",\"currencyCode\":\"EUR\",\"createdAt\":1790352451846,\"updatedAt\":1790979902158}', 1790980115931),
(39, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"EUR\",\"created_at\":1790352451846,\"updated_at\":1791016235733,\"deleted_at\":null,\"version\":24}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":23,\"themeMode\":\"LIGHT\",\"currencyCode\":\"USD\",\"createdAt\":1790352451846,\"updatedAt\":1791014476094}', 1791016235734),
(40, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"USD\",\"created_at\":1790352451846,\"updated_at\":1791016235734,\"deleted_at\":null,\"version\":25}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":23,\"themeMode\":\"DARK\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1791013550823}', 1791066263840),
(41, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"NGN\",\"created_at\":1790352451846,\"updated_at\":1791119663381,\"deleted_at\":null,\"version\":27}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":26,\"themeMode\":\"DARK\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1791107464986}', 1791119663382),
(42, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"DARK\",\"currency_code\":\"NGN\",\"created_at\":1790352451846,\"updated_at\":1791119663382,\"deleted_at\":null,\"version\":28}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":26,\"themeMode\":\"SYSTEM\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1791107469201}', 1791119663385),
(43, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'categories', 'a8f0dd81-d119-40ba-bfd0-4ef04f87b566', '{\"id\":\"a8f0dd81-d119-40ba-bfd0-4ef04f87b566\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"name\":\"Remboursement de prêt reçu\",\"icon\":\"LOAN\",\"color_argb\":4287323382,\"type\":\"INCOME\",\"created_at\":1790920895980,\"updated_at\":1791148540642,\"deleted_at\":null,\"version\":2}', '{\"id\":\"a8f0dd81-d119-40ba-bfd0-4ef04f87b566\",\"baseVersion\":1,\"name\":\"Remboursement de prêt reçu\",\"icon\":\"LOAN\",\"colorArgb\":4287323382,\"type\":\"INCOME\",\"createdAt\":1790920895980,\"updatedAt\":1791144121199}', 1791148540644),
(44, 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'user_preferences', 'bb98b23a-f8c4-41cf-9007-4b3c248814e7', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"user_id\":\"ca0d564e-c72c-478a-8933-ad1797fcfab2\",\"theme_mode\":\"LIGHT\",\"currency_code\":\"NGN\",\"created_at\":1790352451846,\"updated_at\":1791214490149,\"deleted_at\":null,\"version\":30}', '{\"id\":\"bb98b23a-f8c4-41cf-9007-4b3c248814e7\",\"baseVersion\":29,\"themeMode\":\"DARK\",\"currencyCode\":\"NGN\",\"createdAt\":1790352451846,\"updatedAt\":1791201416522}', 1791214490150);

-- --------------------------------------------------------

--
-- Structure de la table `transactions`
--

CREATE TABLE `transactions` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `type` varchar(16) NOT NULL,
  `account_id` char(36) NOT NULL,
  `transfer_account_id` char(36) DEFAULT NULL,
  `category_id` char(36) DEFAULT NULL,
  `date` bigint(20) NOT NULL,
  `description` text NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `payment_method` varchar(32) DEFAULT NULL,
  `fee_transaction_id` char(36) DEFAULT NULL,
  `fee_type` varchar(32) DEFAULT NULL,
  `receipt_id` char(36) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `transactions`
--

INSERT INTO `transactions` (`id`, `user_id`, `amount`, `type`, `account_id`, `transfer_account_id`, `category_id`, `date`, `description`, `latitude`, `longitude`, `payment_method`, `fee_transaction_id`, `fee_type`, `receipt_id`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('00759530-83f0-4908-bb5f-871b9d78099f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789459200000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789478267083, 1789478267226, NULL, 1),
('0199cc38-b576-4736-8665-71ae03c745f5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1788638160000, 'MyNita → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1788800271088, 1788800271001, NULL, 1),
('01c7356f-510f-437b-b0aa-ff390c5ead46', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787229000000, 'Le Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1787376028622, 1787376028622, NULL, 1),
('0249e159-7e8a-431f-a664-520b8605b96e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 123765000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1764621300000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739756928, 1786739756928, NULL, 1),
('0270a602-370c-4e1f-84c3-2341f32aee8d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, NULL, 1791044400000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1791190151219, 1791190152118, NULL, 1),
('02ae6cc8-453f-4268-b162-3f6e7fb6e3ee', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787425560000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787438876827, 1787438876827, NULL, 1),
('030444b2-66b8-4a7e-971f-32436d8021a8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786782420000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1786822116045, 1786822116045, NULL, 1),
('04ba3634-34d1-4be4-afe0-2f775ddf190e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786887000000, 'Alazi', NULL, NULL, NULL, NULL, NULL, NULL, 1786888307352, 1786888307352, NULL, 1),
('051c0342-aea1-481a-bb8d-0b7290e95b32', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786951200000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1786954853366, 1786954853366, NULL, 1),
('05fd0b64-1452-4cf4-8267-40397ac3f5c4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '06a7915c-05f4-40ba-a37f-fd3544ae4339', 1790290800000, 'Il n\'a pas de money', NULL, NULL, NULL, NULL, NULL, NULL, 1790507887172, 1790507887259, NULL, 1),
('0660f7e5-c42f-4313-b2f0-6e3cce639784', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 26527300, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1782937200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738850148, 1786738850148, NULL, 1),
('06e7b5cf-6c83-48a8-b70e-4b57fc263165', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790535600000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790584890099, 1790585114435, NULL, 1),
('0725fd3f-8d57-47d8-82f5-2ca5021be96c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790967000000, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1791023362711, 1791023710427, NULL, 2),
('080dfc60-dce0-440a-91e8-11ee5c456a16', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 600000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1788704880000, 'Un pantalon', NULL, NULL, NULL, NULL, NULL, NULL, 1788773374310, 1788773374429, NULL, 1),
('08166453-7cb4-4145-9d22-138e4b3c0306', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1790796600000, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1790803331643, 1790803332469, NULL, 1),
('08beb613-d69d-4446-99fc-2a32299ba12e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789149600000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789152384614, 1789214638546, NULL, 1),
('08fc0dfd-2922-4aec-b215-c518cad4e78d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788872400000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788909940052, 1788909943862, NULL, 1),
('092e7417-f381-4faf-8430-89a59100778d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1789232460000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1789243366412, 1789243790021, NULL, 1),
('095653bd-6ff4-47de-a070-084430ed97f8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '811ea896-732a-44d8-91c5-1dbc7e974624', 1789979700000, 'Récupération du coli de Abdoul Karim Mon Grand frère', NULL, NULL, NULL, NULL, NULL, NULL, 1790066196550, 1790076620418, NULL, 2),
('09cc5414-92a3-479d-8474-8eb86c1c7909', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787689797937, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787689818837, 1788705596000, NULL, 1),
('0b5cbee0-5a3a-41a6-97fa-1c8a67c8c569', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789131600000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789132634283, 1789132666501, NULL, 1),
('0d332c8a-f93f-40a3-81a7-f922e4b33d43', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 700000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '811ea896-732a-44d8-91c5-1dbc7e974624', 1790015880000, 'Réparation d\'un téléphone', NULL, NULL, NULL, NULL, NULL, NULL, 1790066428103, 1790076620420, NULL, 1),
('0d9af8cc-3227-43c9-aa72-71f5ea9924eb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786710600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740566332, 1786740566332, NULL, 1),
('0dd6f93e-6846-4afa-84de-064d537cc1fd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 7451000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1754079960000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739220439, 1786739220439, NULL, 1),
('0dfaa73f-befa-45c9-84a8-46d003671236', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788440400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788467363440, 1788468101939, NULL, 1),
('0e68558b-72cc-4566-8865-fbe5a9805ec5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1785349380000, '', NULL, NULL, NULL, NULL, 'BANK', NULL, 1786908997246, 1786908997246, NULL, 1),
('0e9e2c9f-b9e8-4797-b1a0-f7c7ebbababd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790362800000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790400643533, 1790401188364, NULL, 1),
('0f8f16cf-fa29-49a4-bf60-1d721369c061', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787425260000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787438593673, 1787438593673, NULL, 1),
('10061963-38af-4391-b7c5-d17bc796c54a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786993200000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787010617291, 1787010617291, NULL, 1),
('103499db-357f-4408-ab9d-3c0a82df074d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 80000, 'TRANSFER', '86f251b7-2294-4d28-b169-debcf9ed0b77', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 1789203660000, 'Amanata → Visa Amanata', NULL, NULL, NULL, 'ad163be2-e105-451f-84a5-7e357dc01d83', NULL, NULL, 1789243534980, 1789243789988, NULL, 1),
('1087805b-64f6-490d-aee5-4a6c76ce87d0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790442600000, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1790506596522, 1790506596644, NULL, 1),
('1128e799-fc41-48f8-902f-ea3571216aa0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787410800000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1787438265572, 1787438265572, NULL, 1),
('1163a5e8-d787-4064-b9cb-e4c690d822ed', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '68f9d5a7-91a1-4027-8339-7849f09ff9bb', 1790012640000, 'Kalid à baypasse un téléphone pour moi à 1500 F je lui est donné 500 en espèce est 1000 F via MyNiata compte à compte', NULL, NULL, NULL, NULL, NULL, NULL, 1790160516817, 1790162930008, NULL, 1),
('11db49c9-9183-4ff1-96be-a54e45182bf1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 12408200, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1756741860000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786722816238, 1786722816238, NULL, 1),
('12444cc8-020c-473e-8953-88d29210ff22', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790254800000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790262621149, 1790265167714, NULL, 1),
('1340e9d7-063b-4777-8ed0-f2fa54d27474', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1710000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1790401597797, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1790401621741, 1790401854547, NULL, 2),
('138d2440-9773-4d93-a0df-e2b04a0c5b52', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1621100, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788941640000, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1788941746117, 1788941746579, NULL, 1),
('13b25353-2c7b-4492-9dba-62ea728b1c7d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790610000000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790846694916, 1790847012484, NULL, 3),
('147fe087-4b62-42d8-923c-29bf2731f672', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790106780000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1790160852654, 1790162930013, NULL, 1),
('15bf9517-0338-4f83-9fb4-374f4aa0964c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790627460000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790672289324, 1790673379103, NULL, 1),
('15d92bf3-11df-4973-bd1f-dd46a1fd3344', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790598600000, 'IPHONE 11 PRO : je fais troc avec mon IPHONE X que je achète à 30 000', NULL, NULL, NULL, NULL, NULL, NULL, 1790672001130, 1790673379095, NULL, 2),
('16892476-9bf4-4457-a0d9-e2b793370812', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 7000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 1790509320000, 'Wave → Mariage', NULL, NULL, NULL, NULL, NULL, NULL, 1790509390540, 1790673379097, 1790673379097, 2),
('16b5c76a-5ae5-4b1a-bfe3-b37f559f6de2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1791140100000, 'MyNita → Espèces', NULL, NULL, NULL, 'daeece49-136b-4a58-99b4-9a2d545bf1d7', NULL, NULL, 1791191716619, 1791191717530, NULL, 1),
('17dc5d9a-5756-4f03-a722-bb308bfffdb8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788390000000, '', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788454495899, 1788455453594, 1788455453594, 2),
('17e0022e-b96f-4de6-8183-4b9b07b6e06f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1789204080000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1789243708313, 1789243789999, NULL, 1),
('180435f6-5906-4fdb-a024-84a0e4ffa35d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1786903200000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1786904308861, 1786904308861, NULL, 1),
('18523a54-ca61-4bef-b456-d7ccb96d8ce9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 4360000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1788267600000, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1788297997747, 1788348529200, NULL, 3),
('18c3e27a-7717-4d93-a654-281e3fb92599', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789736400000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789743009838, 1789743042851, NULL, 1),
('18ed42a1-52b2-4ea2-986c-47a1d44e76e4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787822940000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787909445437, 1788018790523, NULL, 2),
('19520e44-e641-4442-95c6-c3e817fcdffc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 36620000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1751401440000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739102084, 1786739102084, NULL, 1),
('197dd14c-fc5e-493e-a14b-91fa8c3d99cb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788704940000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1788773411160, 1788773411075, NULL, 1),
('199d81fd-05ad-47d9-9e46-ecfb14deb30c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 34521400, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1769978280000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739980231, 1786739980231, NULL, 1),
('19a93f75-5f70-46f2-9b99-c322000d3937', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788030000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788070628628, 1788070628634, NULL, 1),
('19fff101-04e8-4966-af0e-7fa0c96b5798', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789322400000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789325890312, 1789383159923, NULL, 1),
('1ab74ccc-ec2b-4815-bb5f-44f8f31cdaa3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790454000000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790508087902, 1790508747029, NULL, 2),
('1bb8fdf5-e33b-425b-899a-e5789816292a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1789646460000, 'Câble type C', NULL, NULL, NULL, NULL, NULL, NULL, 1789725727346, 1789725738842, NULL, 1),
('1be680bb-5350-4445-abc2-6ef3b0d9dcf8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 13870000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8ff6b4d1-2775-48d0-93a7-dda7b93b5536', 1787209200000, 'QWIPER', NULL, NULL, NULL, NULL, NULL, NULL, 1787213880173, 1787213880173, NULL, 1),
('1c02affe-3906-4aca-9ded-25ffbd4daecc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1790422365927, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790422391722, 1790422415815, NULL, 1),
('1c239808-965b-4f5d-9eee-4f4a27a76eb9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 110000, 'TRANSFER', '63a53de8-284c-4e22-8e32-6f29ea237930', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1788535080000, 'Amanata → Visa Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1788535143573, 1788535203812, NULL, 2),
('1db8d030-e40c-4c2d-a90d-b2c72cbf88c0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788179400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788210057231, 1788210057234, NULL, 1),
('1df8cbab-d7c0-4748-bfe8-f72dbdd84f54', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 1790247267540, 'Espèces → Wave', NULL, NULL, NULL, NULL, NULL, NULL, 1790247300835, 1790400273172, NULL, 2),
('1dfb62c7-6ea7-414c-b7bf-11b70de011c9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788338280000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788341950132, 1788348529162, NULL, 1),
('1e33262e-fc05-4565-8555-057be7948be8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1786697700000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786708542461, 1786708542461, NULL, 1),
('1eb9bff4-ec11-4c71-ab14-d38584b9cd43', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790168400000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790195720088, 1790196522983, NULL, 1),
('1f0dca6b-f65f-4c87-ad02-6f4fdd4fcc2d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 12500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790283960000, 'Boube', NULL, NULL, NULL, NULL, NULL, NULL, 1790334491663, 1790334492479, NULL, 1),
('20d8c048-b807-48cb-a793-da46f6fdb288', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787079600000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787090018654, 1787090018654, NULL, 1),
('20ee1f55-4cac-462a-af48-1c549727d93b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1150000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790442660000, 'Sac', NULL, NULL, NULL, NULL, NULL, NULL, 1790506648241, 1790506648405, NULL, 1),
('20fc4067-3496-4b1d-a982-f7b425e0a8df', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1790610480000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790846980139, 1790846981384, NULL, 1),
('211f853c-5368-4390-8337-439a25b81384', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787785200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787902710131, 1787906410979, 1787906410979, 2),
('213c01e8-346b-4448-8845-ce58a2d863c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787828400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787906642924, 1787906642924, NULL, 1),
('216f99ef-12d8-4af9-ba50-2d882795cbcc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1500000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1790242298941, 'Espèces → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1790242330081, 1790242786030, NULL, 2),
('224ae077-efcf-4cd0-869b-78790707cb0d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787765400000, 'Tenti', NULL, NULL, NULL, NULL, NULL, NULL, 1787818221394, 1787818221396, NULL, 1),
('23021e58-78df-4c50-b6c5-f179883b7175', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1499500, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1785348480000, 'Lavable IA', NULL, NULL, NULL, 'a2a9c32b-343e-46fd-9814-d3b90b340df6', NULL, NULL, 1786908852891, 1786908852891, NULL, 1),
('2348440f-edd6-4776-bb4a-3447bd17e472', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788854400000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788857869017, 1788858070556, NULL, 1),
('23c8e707-4784-4fa6-b6a9-da862f9eff75', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 506100, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788942060000, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1788942110193, 1788942110609, NULL, 1),
('245d517a-2e31-41d1-a4e9-37cf8b3f592d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1786808520000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1786826880938, 1786826880938, NULL, 1),
('2580cc26-3da1-422e-8ba1-d6d5c34ceba1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', '', NULL, '', 1787742951000, 'Transaction Postman', NULL, NULL, NULL, NULL, NULL, NULL, 1787742951000, 1787742951000, NULL, 1),
('25fb2223-1545-4419-977f-8a5111849483', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1789858800000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1789934168639, 1789934605950, 1789934605950, 2),
('264890c9-6240-420e-a3ac-e4de91733ea9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790103600000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790160080143, 1790162930002, NULL, 1),
('269b539d-38a4-4121-bfa0-752fc824706e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1788192000000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1788214750364, 1788256443002, NULL, 2),
('27024c0b-c0d7-4c32-88f4-2a5884e6588e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 8145300, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1759348620000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786737499259, 1786737499259, NULL, 1),
('270845eb-c814-45f2-bcb0-66f21075311d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 160000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1790195776845, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1790195798334, 1790196522988, NULL, 2),
('271a52e6-4193-44f7-815a-2d516c95c42c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1789203660000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1789214514743, 1789214638537, NULL, 1),
('2765dfe4-21cd-4fd2-b8cf-aae40f25df41', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1790290800000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1790401350808, 1790401854545, NULL, 1),
('27c07039-3529-47ae-82b5-0c1f9af50065', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790357280000, 'Réparation du frein de Moto', NULL, NULL, NULL, NULL, NULL, NULL, 1790400603823, 1790401188362, NULL, 1),
('27c2a71e-149a-4b9a-965a-37c3d584c743', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789063200000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789067930274, 1789069460636, NULL, 1),
('27f36fc9-2ee6-421d-abe8-7c0121db73d3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1790627400000, 'Savon', NULL, NULL, NULL, NULL, NULL, NULL, 1790672247618, 1790673379102, NULL, 2),
('27f48ee4-de20-405c-a71b-9f3b127f1368', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789585200000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725448063, 1789725449227, NULL, 1),
('287eda82-39ab-4c66-add5-14d94081ea8b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787248980000, 'Sadaka', NULL, NULL, NULL, NULL, NULL, NULL, 1787302318940, 1787302318940, NULL, 1),
('2906fd04-a394-48fe-9974-f9084779c957', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', '86f251b7-2294-4d28-b169-debcf9ed0b77', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 1788534660000, 'Amanata → Visa Amanata', NULL, NULL, NULL, '3d26e740-c5b5-4cb7-9ae9-49ae9728946e', NULL, NULL, 1788534738094, 1788534738951, NULL, 1),
('29812836-791e-4838-943f-e2783dcdd9d0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788267600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788270107697, 1788270107697, NULL, 1),
('298dbd25-e0da-4f8f-8a13-d7ab2436f02b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1785345360000, '', NULL, NULL, NULL, NULL, 'BANK', NULL, 1786908691257, 1786908691257, NULL, 1),
('29dc44d3-079b-4b55-9412-31ef06b02e78', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 58103000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1748809320000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739051085, 1786739051085, NULL, 1),
('29f1bb08-9e61-4c83-99a4-2cb29604643e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788458700000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788467808066, 1788468101950, NULL, 1),
('2a0d8842-415a-4139-a2ef-e5c924fb0b61', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786521600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786571722752, 1786571722752, NULL, 1),
('2a3e77d6-8b9a-4994-a2d4-d423dbaac29e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788076800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788127024997, 1788127024997, NULL, 1),
('2a7b1f46-8851-4a56-98e6-df47624e8920', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788940800000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788941284345, 1788941284780, NULL, 1),
('2aa3d946-a864-47cb-ad7f-d902fa7c0b15', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2010000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 1788298380000, 'Espèces → Wave', NULL, NULL, NULL, NULL, NULL, NULL, 1788298449801, 1788298449801, NULL, 1),
('2aca2d42-c6c4-4393-8198-6308f0332422', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790688660000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790785092147, 1790785112857, NULL, 2),
('2acd095b-2681-4784-abaa-8876d8691fe6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788089400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788127120421, 1788127120421, NULL, 1),
('2adb57be-a872-4007-a282-0cc688c3fc6e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787990400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788019106310, 1788019106310, NULL, 1),
('2c261274-9133-4aff-b5b9-838e5016a5a6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 4884300, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1762027080000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786737591927, 1786737591927, NULL, 1),
('2ce9213e-c722-4382-98e4-5f4f7e873412', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'TRANSFER', '63a53de8-284c-4e22-8e32-6f29ea237930', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1788534780000, 'Visa Amanata → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1788534897247, 1788534898121, NULL, 1),
('2d265038-f1b5-40fe-8134-8ece8bea1037', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788768000000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788772065346, 1788772065257, NULL, 1),
('2e89d212-ec4e-40c5-8ea0-b1a1bf737b63', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790324760000, 'Sadaka', NULL, NULL, NULL, NULL, NULL, NULL, 1790328495464, 1790328496172, NULL, 1),
('2eba22f3-a65b-4bde-9de3-576caa870981', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786969800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786978547283, 1786978547283, NULL, 1),
('300834d4-009c-45a2-ba44-8ef6e05cb2de', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1789725720000, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1789725878708, 1789725894171, NULL, 2),
('30457352-49c3-46cf-9048-0e19e0a2f726', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 300000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1791132000000, 'Montre', NULL, NULL, NULL, NULL, NULL, NULL, 1791190699097, 1791190700324, NULL, 1),
('30de2572-18b5-4f11-a22b-0b783f2ccdf6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788004800000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788081875521, 1788081894968, NULL, 2),
('3117f2c1-aeaa-45d0-b91b-27a3df8e4f71', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8e4f9cf0-c631-489d-be3a-757f533fb3f1', 1787772254837, 'Achat test', NULL, NULL, NULL, NULL, NULL, NULL, 1787772262074, 1787818070728, 1787818070728, 2),
('314a831d-64ee-42f4-92ea-f07c8454f067', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790859600000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790889826890, 1790889827353, NULL, 1),
('31546534-cbd4-4621-81f9-d5aa6053967f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 300000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1786728600000, 'Vidage de moto 🏍', NULL, NULL, NULL, NULL, NULL, NULL, 1786741104366, 1786741104366, NULL, 1),
('32e161aa-47ad-4438-a1e2-60b8367a9f66', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788695040000, 'Aïcha', NULL, NULL, NULL, '6045a413-2030-4c31-b1bb-d6b8ec52b80c', NULL, NULL, 1788702316763, 1788703064492, 1788703064492, 2),
('334751b4-e103-425b-b4fb-cf7e143e7641', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1785353280000, '', NULL, NULL, NULL, NULL, 'BANK', NULL, 1786910916870, 1786910916870, NULL, 1),
('335e9f5a-b7ac-4f85-8490-ec2068b55a50', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789372800000, 'Petit déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789478358592, 1789479113802, NULL, 1),
('33adf9ce-ea28-440d-ac12-a7f38a287e53', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788422400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788467337150, 1788468101902, NULL, 1),
('341a5eb7-674b-4960-97e3-95e09a8054a8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790582400000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790584937442, 1790585114437, NULL, 1),
('3474b4db-0582-4350-ae6d-9791be9d477f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1789916820000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1789862886613, 1789862974583, NULL, 1),
('34cfec04-cd55-4aab-a357-04727a99ad2d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790499840000, 'Abdoul Rachid', NULL, NULL, NULL, NULL, NULL, NULL, 1790507120737, 1790507166299, NULL, 2),
('353398bd-e6bb-4902-8623-0a004db43b9c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '811ea896-732a-44d8-91c5-1dbc7e974624', 1787421600000, 'Sady-group', NULL, NULL, NULL, NULL, NULL, NULL, 1787438514920, 1787438514920, NULL, 1),
('355e9adb-50ca-45ce-9502-4e4b3a1d2471', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'f0fe0918-0b8f-430e-abaf-a8e3075edf05', 1787491800000, 'Bavette + Lotis', NULL, NULL, NULL, NULL, NULL, NULL, 1787497209807, 1787497209807, NULL, 1),
('35bd2000-3727-4954-877f-0ef59ff1ff81', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787056200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787089947154, 1787089947154, NULL, 1),
('36659d65-9dd4-4384-9bc1-32063844d3c1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1789837500000, 'Police', NULL, NULL, NULL, NULL, NULL, NULL, 1789862780883, 1789933901174, NULL, 2),
('374fc539-b0ba-4491-8f71-8996bb967488', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788872400000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788873491314, 1788936039520, NULL, 2),
('37c7d925-db3f-4290-b300-114f87c7444f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788039360000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788082740180, 1788082740180, NULL, 1),
('37f35ee7-87bc-4ed3-af52-4651e1a40cc0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 1790326020000, 'Espèces → Wave', NULL, NULL, NULL, NULL, NULL, NULL, 1790506095271, 1790506095349, NULL, 1),
('3823c0c0-2931-4994-a95d-d8b718d89230', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8e4f9cf0-c631-489d-be3a-757f533fb3f1', 1787820506577, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787820520994, 1787873564018, 1787873564018, 2),
('394ecac4-d6ab-4955-9bfb-60d3e52d5b3a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788804000000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788909938048, 1788909943846, NULL, 1),
('396ba647-79b8-47d0-aed2-c7cff29f0a3b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787338800000, 'Le dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1787375141405, 1787375141405, NULL, 1),
('39c60515-2646-4902-8c5a-c04a762b0985', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1786822133649, 'Coiffure', NULL, NULL, NULL, NULL, NULL, NULL, 1786822265160, 1786822265160, NULL, 1),
('3a1c2bd2-fa0e-4341-a6b2-13269142076e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788526800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788528208364, 1788528209546, NULL, 1),
('3af8ea23-6865-44f2-8628-e7c6f3eae3bc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787401860000, 'Pommes', NULL, NULL, NULL, NULL, NULL, NULL, 1787430982156, 1787430982156, NULL, 1),
('3b03d804-6f37-409b-b390-434e3428827b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787593140000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787560809496, 1787560809496, NULL, 1),
('3b18667e-2882-4d07-9db8-ec1c09904962', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1789407435556, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1789407444691, 1789478227682, 1789478227682, 2),
('3b22d925-b1d8-42a4-a8fe-925a8f6144cf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1785343560000, '', NULL, NULL, NULL, NULL, 'BANK', NULL, 1786908354679, 1786908354679, NULL, 1),
('3b8c4883-1006-420e-bbbd-fc5c25734778', '5e1c8db5-c66b-48bc-8b53-d43a1fc8e701', 10000, 'INCOME', 'fb503ad8-df81-4f28-b8b1-f737ef850345', NULL, 'b4a55e18-6c14-4127-817c-1b2974d141c3', 1791073563205, '', NULL, NULL, NULL, NULL, NULL, NULL, 1791073575266, 1791073577950, NULL, 1),
('3bb53764-a397-43ea-9e30-2db4adaf8fa3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790610240000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'BANK', NULL, 1790846915168, 1790846916378, NULL, 1),
('3c5ea9a4-a5fb-4454-9cab-cd492441de7c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790323200000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790400287862, 1790401188348, NULL, 1),
('3d26e740-c5b5-4cb7-9ae9-49ae9728946e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788534660000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788534737810, 1788534738762, NULL, 1),
('3f6a276d-4291-4ae9-8a48-2bcb08a2ce45', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1785353940000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786911374602, 1786911374602, NULL, 1),
('3f867a34-7257-40f2-b850-ca3b7383a42e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30220400, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1769976840000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738507315, 1786738507315, NULL, 1),
('40c29237-582f-4d8d-9372-7e7172a53b5a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 450000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790442720000, 'Anti Refilais', NULL, NULL, NULL, NULL, NULL, NULL, 1790506729399, 1790506729500, NULL, 1),
('40e497fb-2846-4928-8be8-835c260fbaaf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788694380000, 'Aïcha', NULL, NULL, NULL, 'df178bf8-39bb-4596-8e81-1971f4659980', NULL, NULL, 1788798869219, 1788798986449, NULL, 1),
('41096854-ddc7-4cee-ae0c-fa7bb7d3771f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 299600, 'TRANSFER', '63a53de8-284c-4e22-8e32-6f29ea237930', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1790665740000, 'Visa Amanata → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1790847829311, 1790847830511, NULL, 1),
('4120f8f8-c8f3-4cf0-a79a-c2820e1111f7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1791131400000, 'Chemise', NULL, NULL, NULL, NULL, NULL, NULL, 1791190649357, 1791190650267, NULL, 1),
('41ba6d6d-57ed-40d5-928d-ed1b62258e74', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788040800000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788082395678, 1788082395678, NULL, 1),
('42afb86a-6e57-4cd2-a1a9-eaa9ec8d4873', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788893760000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788935823599, 1788935838029, NULL, 1),
('42e9dd67-d44d-46f6-b6d3-bafe84d3b154', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789218000000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789406015540, 1789406017243, NULL, 1),
('4307f129-a9c2-43d6-b6fc-3ea18ba07730', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1787148000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787203837413, 1787203837413, NULL, 1),
('4368fed3-235c-434b-9acc-91496b826892', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1789927200000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1789933996354, 1789933997018, NULL, 1),
('43a2cfc0-8f1d-4354-b6a1-5061a0c83ac9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'INCOME', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '70307aa9-3454-4864-b81a-a423354532ee', 1785353280000, '', NULL, NULL, NULL, '334751b4-e103-425b-b4fb-cf7e143e7641', NULL, NULL, 1786910916862, 1786910916862, NULL, 1),
('43fab1eb-0e99-41cd-b2a3-e0cad49f1c8d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 570000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1785354180000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786911514379, 1786911514379, NULL, 1),
('448b25b3-14f8-4260-8643-2872e681a50e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 80000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790452440000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790506535570, 1790506535637, NULL, 1),
('44c72f59-6ec2-45d4-8705-d3a037b425de', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790017200000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790065686502, 1790066033132, NULL, 1),
('44c963f4-4c52-41f2-a031-ba086a434816', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790622000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790673336777, 1790673379106, NULL, 1),
('45e1d84d-517e-4c4b-b06a-18b1094f14d3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789909200000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789933796202, 1789933796940, NULL, 1),
('469ca46a-c5fc-4052-b4fe-84b02cab6c1e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788804000000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788804209952, 1788936039516, 1788936039516, 2),
('474c068a-b270-403b-bc9c-f1b31bf96604', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789408800000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1789478317050, 1789479113786, NULL, 1),
('474de49d-9f46-4f53-87ea-1156222b8411', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788558360000, 'Nita', NULL, NULL, NULL, NULL, NULL, NULL, 1788558397977, 1788558398984, NULL, 1),
('47852203-78a7-4eee-a788-a9d026e40b89', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1167200, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1788040800000, 'Claoud Pro', NULL, NULL, NULL, '41ba6d6d-57ed-40d5-928d-ed1b62258e74', NULL, NULL, 1788082395979, 1788082395979, NULL, 1),
('47e89f01-ccb8-46ce-997a-e0bff21b1d51', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787079600000, 'Abdoul Karim', NULL, NULL, NULL, NULL, NULL, NULL, 1787090064530, 1787090064530, NULL, 1),
('4863ff4a-9996-42a8-920a-7f545f8075a4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790879040000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1790889861873, 1790889862248, NULL, 1),
('489b516a-6690-4701-b69a-4832cab9df02', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788675780000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788773087948, 1788773087911, NULL, 1),
('4977ff57-8fb4-4061-aa23-1cbc1daf3311', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1791100800000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1791190250771, 1791190251684, NULL, 1),
('4aa8c428-3e59-4959-8afb-87cb3c063849', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1769980140000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828213297, 1786828213297, NULL, 1),
('4af1fa7f-9945-4809-89e3-79dc6a8c82ab', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1791131100000, 'Sirage', NULL, NULL, NULL, NULL, NULL, NULL, 1791190603433, 1791190604343, NULL, 1),
('4b64d07a-0c68-4547-8d41-19826dcd342c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1790945580000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790945615002, 1790945634544, NULL, 1),
('4c247258-24db-4046-b918-bf796e0622b8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790241607479, 'Cadeau de Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1790241647244, 1790242786024, NULL, 1),
('4d55ff91-507a-4bb3-888e-44150df2257d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790771160000, 'MyNita → Espèces', NULL, NULL, NULL, '954097a7-0f53-4b51-b801-a2bfb9840dbe', NULL, NULL, 1790803619700, 1790803620516, NULL, 1),
('4d935db9-67c2-423a-a926-8a041086dc3b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788297120000, 'Nana la file de Djbo', NULL, NULL, NULL, NULL, NULL, NULL, 1788297711226, 1788297711226, NULL, 1),
('4e93ef10-f824-4eee-88ee-bed152e2e823', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1789479000000, 'Œuf', NULL, NULL, NULL, NULL, NULL, NULL, 1789479075680, 1789479113850, NULL, 1);
INSERT INTO `transactions` (`id`, `user_id`, `amount`, `type`, `account_id`, `transfer_account_id`, `category_id`, `date`, `description`, `latitude`, `longitude`, `payment_method`, `fee_transaction_id`, `fee_type`, `receipt_id`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('4ee66836-34d1-4b37-874b-836972904a7c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '06a7915c-05f4-40ba-a37f-fd3544ae4339', 1789117281133, 'Emprunt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1789117401545, 1789117578227, NULL, 1),
('4ef8f44c-1424-4f92-afb5-8d36453c1341', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 1790401903207, 'Wave → Mariage', NULL, NULL, NULL, NULL, NULL, NULL, 1790401936762, 1790506126703, 1790506126703, 2),
('4fece633-6549-4be0-813b-59d1a7f42f2f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787904000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787906801472, 1787909297862, NULL, 2),
('4ff8bcc3-133a-400b-af62-55a3725b05ae', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786734000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740634674, 1786740634674, NULL, 1),
('5102227e-6c22-4a5c-9f46-5e650591d6e8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1782940500000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828573213, 1786828573213, NULL, 1),
('52039a91-deb3-4b78-b19c-fcd07fc0583b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787250000000, 'Révision de Moto', NULL, NULL, NULL, NULL, NULL, NULL, 1787302150102, 1787302150102, NULL, 1),
('526ac681-2675-46f2-a830-0bf66c71d0f2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790236800000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790242839774, 1790242846417, NULL, 1),
('52db2605-a8c0-4c30-a2de-dbc2064f1af6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790596800000, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1790672072085, 1790673379098, NULL, 1),
('52e2410a-edd6-4d81-95f3-c7e087c8a08c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1788019980000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788127840928, 1788127840928, NULL, 1),
('52ee1ba8-a096-4c72-8ff7-3c527ccda3ac', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '06a7915c-05f4-40ba-a37f-fd3544ae4339', 1790463600000, 'Je donne un avance de 3000F sur 5000F ca reste 2000F', NULL, NULL, NULL, NULL, NULL, NULL, 1790507681032, 1790507681116, NULL, 1),
('53b33f4a-80ff-4a02-accf-01dd4dcaa3d1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1787900400000, 'MyNita → Espèces', NULL, NULL, NULL, 'ac4eba3c-028c-4dba-b02d-2c49741dfc19', NULL, NULL, 1787910689507, 1787910689507, NULL, 1),
('53bbc1fa-e241-4b7b-974a-ac67fb31c0cc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 55000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788285600000, 'Nana (ma sœur)', NULL, NULL, NULL, NULL, NULL, NULL, 1788342092093, 1788348529180, NULL, 1),
('54036bf9-caa2-4ab1-ac52-24d422b07edf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790716620000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790803084410, 1790803085234, NULL, 1),
('54328d6d-3b89-4faa-ab30-7b6721d93548', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790062200000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1790160180045, 1790162930004, NULL, 1),
('5437a9ab-e927-466f-a3ca-631866309669', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790965740000, 'Réparation de moto', NULL, NULL, NULL, NULL, NULL, NULL, 1791023449092, 1791023710424, NULL, 1),
('547acab6-3b7b-4a5d-bbfb-3d3380408497', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787817600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787906520274, 1787909318078, NULL, 2),
('5494a9d6-3481-4eab-8791-5c1e4e6a56d2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1788109560000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1788127649981, 1788127649981, NULL, 1),
('55b12c47-46e7-4d46-8063-a26b7a1c94ce', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 35000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786608000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786657400956, 1786657400956, NULL, 1),
('55fb8a20-29bb-4599-85d1-d8485ebb5e04', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786647600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786657847228, 1786657847228, NULL, 1),
('5633c126-8b80-49ff-8eb6-4e089b546fb0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'INCOME', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1787410800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787689975627, 1788705596000, NULL, 1),
('566bed35-00e4-48a3-ba05-67f8cbcbbd1b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1786539600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786571877590, 1786571877590, NULL, 1),
('566f7f4e-33a0-4194-95de-b2714880ee5b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1791039960000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1791190103385, 1791190104277, NULL, 1),
('56e44f7a-0034-4f67-8b1c-2226d1bd5433', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788854400000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788909939046, 1788936039497, 1788936039497, 2),
('56f9f232-d140-4151-afa4-4758b0144d83', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 150000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1791140280000, 'Je lui est acheter un cable type-c iphone', NULL, NULL, NULL, NULL, NULL, NULL, 1791190830750, 1791190831723, NULL, 1),
('57f2ca51-b6d6-493d-8afb-f4c31a92131a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786624200000, 'Sadaka', NULL, NULL, NULL, NULL, NULL, NULL, 1786657762583, 1786657762583, NULL, 1),
('58f4ab76-22b5-4c32-b80e-aed4cf8c32d6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 432500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1787690389247, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787690403704, 1788705596000, NULL, 1),
('59bd2344-2220-47eb-a8f6-a3c0f0935ffc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787126400000, 'Le petit-déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1787376119194, 1787376119194, NULL, 1),
('5ab03a0b-0327-4348-8505-20273fc9c372', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787256000000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787302075493, 1787302075493, NULL, 1),
('5ae87b3e-a010-4149-bc57-c64132588214', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25330500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1772396100000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738589377, 1786738589377, NULL, 1),
('5b099afe-7c15-4e2d-937a-c0c839377486', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 800000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1790686080000, 'MyNita → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1790848161480, 1790848162754, NULL, 1),
('5bc6440b-bbf2-415e-b102-20acd6dc3634', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1789830300000, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1789862749989, 1789933885884, NULL, 2),
('5c63da13-4435-4013-99bd-b730e0df3988', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788202800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788210191101, 1788210290238, NULL, 2),
('5de75c74-7b6a-4ba3-9a90-76c15d6fc92e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787902200000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1787906777216, 1787909279882, NULL, 2),
('5e0282fd-ff76-40d4-81ba-5d01e6fa42a2', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 50000, 'EXPENSE', '97d3f709-8069-4963-8c32-874009967823', NULL, 'eddc8008-9e31-4ff9-ac07-ac3d50401a8f', 1790845377472, 'Wifi bill', NULL, NULL, NULL, NULL, NULL, NULL, 1790845411526, 1790866983239, NULL, 1),
('5e28d664-8bf0-4881-a282-d7bc57710c13', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787990400000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788019148375, 1788019169538, NULL, 2),
('5e68a759-9180-4440-b2e5-9ffadcf86c38', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1790669580000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790673206940, 1790673379105, NULL, 1),
('5ec6e079-18f4-4b28-914a-c514bbc50fd7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790508480000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790508613002, 1790508613112, NULL, 1),
('5f93ef3b-5aeb-4ad5-80e9-042a3905837b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1788109200000, 'Wave → Espèces', NULL, NULL, NULL, '9267b0bf-c145-423f-aee4-eb9cf030fba7', NULL, NULL, 1788127500540, 1788127500540, NULL, 1),
('6045a413-2030-4c31-b1bb-d6b8ec52b80c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 90000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788695040000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788702316773, 1788703064482, 1788703064482, 2),
('60487f4d-6b1c-4b25-9374-8670421803f9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790341200000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790400317852, 1790401188350, NULL, 1),
('60d38e1f-1a9f-4478-ab4e-80d2b0833db1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'INCOME', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '70307aa9-3454-4864-b81a-a423354532ee', 1785349380000, '', NULL, NULL, NULL, '0e68558b-72cc-4566-8865-fbe5a9805ec5', NULL, NULL, 1786908997240, 1786908997240, NULL, 1),
('60f24f77-62cb-4009-aa1e-9466bfae8f46', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1780348440000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828519643, 1786828519643, NULL, 1),
('61d8b0b5-80c3-4cf5-a41f-4bedad47f5f1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790602200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790672129270, 1790673379099, NULL, 2),
('6222af30-cc43-4c80-bb73-e769c587838c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1787214915180, 'Rembourser les emprunt de Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1787214930899, 1787214930899, NULL, 1),
('62ff81db-07a2-473e-a4f8-a851a72f791d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 300000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1790242205768, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1790242232750, 1790242786028, NULL, 1),
('634c15ce-eb79-45ef-87cc-3e3aade50744', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 12186400, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1756758420000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739257706, 1786739257706, NULL, 1),
('63ae06a4-b089-4fa3-949b-8bd941c9b9c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1791140640000, 'Son contribution pour acheter Anana', NULL, NULL, NULL, NULL, NULL, NULL, 1791191145835, 1791191152596, NULL, 1),
('64158975-5e79-4806-947a-dd870b18d85f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1786808520000, '', NULL, NULL, NULL, '245d517a-2e31-41d1-a4e9-37cf8b3f592d', NULL, NULL, 1786826880932, 1786826880932, NULL, 1),
('64c885ef-8b48-48bd-80fa-1621e7b494c4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 39500000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1782938760000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740447199, 1786740447199, NULL, 1),
('651b051e-b808-4b7d-ba73-56b8fcf704da', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1785618960000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828636832, 1786828636832, NULL, 1),
('66de276e-6fe2-401e-9904-7a8bcc4450fd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 458500, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1788296400000, 'Achat d\'un nom de domaine naniger.com', NULL, NULL, NULL, '6d1c24ef-bf5d-4f3c-b3f7-a7ffc8caad42', NULL, NULL, 1788344125265, 1788348529251, NULL, 2),
('67797ae7-09ff-4a56-a3bc-7eee0e07f768', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787212800000, 'Le petit-déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1787375898549, 1787375898549, NULL, 1),
('67958d73-c6db-4afc-836c-b44ec60a77ed', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 24428400, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1772397600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740076250, 1786740076250, NULL, 1),
('67c3e26d-d302-4fb1-b503-aec63c14bfb5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1788893760000, '', NULL, NULL, NULL, '42afb86a-6e57-4cd2-a1a9-eaa9ec8d4873', NULL, NULL, 1788935692096, 1788935838039, NULL, 2),
('67c68b9b-9880-4e00-b4aa-abeed1af0613', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788458400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788467461213, 1788468101945, NULL, 1),
('685c166b-46f8-4147-91d0-ee4f7a53c554', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789045200000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789049631693, 1789049632049, NULL, 1),
('68e2ff3b-b825-4091-b354-88c7bd743145', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 20000, 'EXPENSE', '97d3f709-8069-4963-8c32-874009967823', NULL, 'c3eb7499-816c-4822-826e-9f0368ceaedb', 1790581177648, '10', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790581214544, 1790651994694, NULL, 1),
('694a2c87-f68b-4535-8438-287bfecbb655', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '06a7915c-05f4-40ba-a37f-fd3544ae4339', 1786740734105, 'Emprunt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1786740833775, 1790401188367, 1790401188367, 2),
('698fa708-4024-4d26-bf15-0dfa02f46b43', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787142600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787203732225, 1787203732225, NULL, 1),
('6a1218be-62a0-4f7a-a727-a1c063525e76', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 35000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787491800000, 'Lipton + Biscu', NULL, NULL, NULL, NULL, NULL, NULL, 1787497119260, 1787497119260, NULL, 1),
('6b08e038-622a-4805-890f-d9a3a6f8f384', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1789226220000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790263045460, 1790265167717, NULL, 1),
('6b184672-4c8e-40f4-b081-c4d31a05a279', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 28000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788344395919, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788344405772, 1788348529266, NULL, 1),
('6b3259ee-4399-4192-988e-575a9d050c6c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790967300000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1791023580375, 1791023710428, NULL, 1),
('6b5cd2ff-fca4-433f-82a2-dfb88124f366', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1790846220000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790846468888, 1790846470144, NULL, 1),
('6b72e7a5-975c-4206-adeb-758cddfcaaaa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 31256500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1751384940000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786722605831, 1786722605831, NULL, 1),
('6bdd14f0-dc0d-4668-a101-28e7e064d1ef', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790098200000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1790160216574, 1790162930007, NULL, 1),
('6c13b429-0012-42aa-856f-0088728e6e5a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1789204020000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1789243669390, 1789243789993, NULL, 1),
('6d1c24ef-bf5d-4f3c-b3f7-a7ffc8caad42', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788296400000, '', NULL, NULL, NULL, NULL, 'BANK', NULL, 1788344125270, 1788348529246, NULL, 2),
('6d3aabaf-259f-4234-b24e-9a5c57320a78', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 75000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788631200000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788646379313, 1788646402120, NULL, 1),
('6d7daab0-f0a2-4ad1-9648-d55ae1c169a5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787401740000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1787438040603, 1787438040603, NULL, 1),
('70a262ff-b3e2-46de-b763-bb6e910f4fec', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790713320000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790803428851, 1790803429734, NULL, 1),
('70b665c9-e128-49e2-be97-f7dc5843d8d6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 400000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1790204400000, 'Remboursement d\'emprunt', NULL, NULL, NULL, 'ade495ea-82ee-4ecb-ba91-64e5851e9463', NULL, NULL, 1790243755631, 1790261486404, NULL, 3),
('71080d74-d9ed-4f5a-9157-cbecf994486b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1775077980000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828428133, 1786828428133, NULL, 1),
('7114b802-dc67-4e10-960e-c53db050825d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790271060000, 'Tête de chargeur type C', NULL, NULL, NULL, NULL, NULL, NULL, 1790400786989, 1790401188365, NULL, 1),
('719d9f96-762d-4e9c-93c9-cd6ddda94f6a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790514000000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790584821458, 1790585114075, NULL, 1),
('72166804-ffc3-4642-beb3-455d01ccd560', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789027200000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789032085938, 1789032126939, 1789032126939, 2),
('724af8a7-c8d3-4b30-a078-9e40689b92be', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1788600240000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1788773147327, 1788773164204, NULL, 2),
('72509dbc-dc6d-4f2f-87ec-3afaec29f9c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 88400, 'TRANSFER', '63a53de8-284c-4e22-8e32-6f29ea237930', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1788291000000, 'Visa Amanata → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1788344212436, 1788348529260, NULL, 1),
('72db4f02-51ab-44f1-9096-ea240ac53ce1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1789340400000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1789478516111, 1789479113811, NULL, 1),
('72e13c34-9bcf-44a7-820a-3d84ef11f122', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1789513200000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1789725246346, 1789725246884, NULL, 1),
('731bddaa-833f-4cca-8da0-cc83d672c1af', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1785338280000, 'Solde insuffisant', NULL, NULL, NULL, NULL, NULL, NULL, 1786907879871, 1786907879871, NULL, 1),
('737c095c-abfb-4f27-b1b0-975be4a5ce53', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '969728c5-9d6f-42d4-9e40-bb0f1827f955', 1790841900000, 'Cadeau de la part d\'Abdallah Qwiper', NULL, NULL, NULL, NULL, NULL, NULL, 1790848727394, 1790938370048, NULL, 2),
('74381184-990e-493f-9dcd-5798ed623da7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787387400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787395061996, 1787395061996, NULL, 1),
('75016e45-7d91-4320-b826-82e4568ac6f7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 18881000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1767298200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738413542, 1786738413542, NULL, 1),
('75501bcb-e80b-47b7-a0a4-5345e30503f3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789891200000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789862673015, 1789862974550, NULL, 1),
('759c5008-8451-4927-9ca6-479a910ad3ba', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1788811020000, 'Réparation de mon pantalon et chemise', NULL, NULL, NULL, NULL, NULL, NULL, 1788857950387, 1788936039508, NULL, 2),
('75a9724f-aa0b-4409-9ad7-ddcdf647b4f4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1789232460000, '', NULL, NULL, NULL, '092e7417-f381-4faf-8430-89a59100778d', NULL, NULL, 1789243366406, 1789243790026, NULL, 1),
('7681fa5c-b0ac-4084-95ec-aa9145d275d9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1300000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1790241466939, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1790241500887, 1790242786023, NULL, 1),
('76febf68-e42d-432a-a19c-e2aa0479f754', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790255400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790262664610, 1790265167715, NULL, 1),
('77ce6c39-ae3c-4001-b99f-aa99576dc626', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1786797180000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1786822426733, 1786822426733, NULL, 1),
('77d4983f-bdca-4d6c-a87f-f1222f15678a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786694400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786708508324, 1786708508324, NULL, 1),
('796f7fe1-1d50-4414-bf9c-2eb6edc76e49', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789804800000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789933783402, 1789933856502, NULL, 2),
('7a507cc9-2660-448f-ac32-331b393a7543', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1500000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1789478607317, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1789478619167, 1789479113825, NULL, 1),
('7b1cfb3b-14d5-4940-9a09-00c1164163da', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1300000, 'TRANSFER', '86f251b7-2294-4d28-b169-debcf9ed0b77', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 1788033600000, 'Amanata → Visa Amanata', NULL, NULL, NULL, 'be2723b1-7874-42e5-bad8-e173915519d5', NULL, NULL, 1788070764489, 1788070764498, NULL, 1),
('7b46bb3d-db5a-4822-adec-ee5755ec6e14', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1772399400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828274695, 1786828274695, NULL, 1),
('7bdecab8-46e2-47f8-8a39-4d1682a56941', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3500000, 'INCOME', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '811ea896-732a-44d8-91c5-1dbc7e974624', 1791018600000, 'Héritage', NULL, NULL, NULL, NULL, NULL, NULL, 1791191605025, 1791191616690, NULL, 2),
('7c81341c-d46c-45d6-99c9-bb0315662c8d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790534520000, 'Abdoul Rachid NinoTech', NULL, NULL, NULL, NULL, NULL, NULL, 1790585003230, 1790585114438, NULL, 1),
('7cfe05d7-853c-4138-bc32-0cbeaf9722d4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790431800000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1790506423994, 1790506424313, NULL, 1),
('7d516862-75d8-4485-964b-8a8c169cf29e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1789806600000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1789862697293, 1789933870685, NULL, 2),
('7dd1ccc8-5727-449e-8945-679218ec52bd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 37500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786867200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786878241594, 1786878241594, NULL, 1),
('7ddfd1f2-5b97-4838-bb9c-761b95668d4d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787148000000, 'Boissons', NULL, NULL, NULL, NULL, NULL, NULL, 1787214274299, 1787214274299, NULL, 1),
('7e9b2ebf-a0db-4dce-9d88-86d401389089', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787425200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787438566926, 1787438566926, NULL, 1),
('7eb1923e-9e42-4e08-9678-c8513b111999', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790773200000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790803126046, 1790803126873, NULL, 1),
('7f80c447-f151-4fdb-b9cb-3a5bdda994c4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790946358144, 'Reçu de Abdoul Kader Bachir (0022794961793)', NULL, NULL, NULL, 'd9fc6cbb-a681-4f2e-9f26-72262c26029c', NULL, NULL, 1790947256935, 1790947415824, NULL, 1),
('7ffe0af0-0953-46be-b601-aa95de8fb0af', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786797060000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786822318901, 1786822318901, NULL, 1),
('80573240-94ca-4872-92a1-8adfae5f9cd5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1787394900000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787690158287, 1788705596000, NULL, 1),
('806c153f-8c7a-417e-8553-2c107486207f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786883400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786888271441, 1786888271441, NULL, 1),
('808bf5bf-82ee-4049-b01e-553b8f525dc1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790847180000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'BANK', NULL, 1790847310409, 1790847311657, NULL, 1),
('810ed723-a80a-4813-a879-8cbe9828a936', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 300000, 'INCOME', '31110101-1485-4c31-a798-42d699b5de65', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1790261100000, 'Payement Cadeau (Nana Hadiza et Nana) de ma part', NULL, NULL, NULL, NULL, NULL, NULL, 1790261253575, 1790261275163, NULL, 2),
('8138c3ff-cbbc-4116-b3cf-d875910fb9c3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '68f9d5a7-91a1-4027-8339-7849f09ff9bb', 1790013000000, 'Kalid à baypasse un téléphone pour moi à 1500 F je lui est donné 500 en espèce est 1000 F via MyNiata compte à compte', NULL, NULL, NULL, 'f8ac5826-174b-485c-9bea-4dfb529375cb', NULL, NULL, 1790160714654, 1790162930010, NULL, 1),
('81f69340-ac2d-4898-9139-bc3dc95c89a3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786561200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786571822094, 1786571822094, NULL, 1),
('81f7faf1-6723-4d82-ade2-d58a83b71f93', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1786604400000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1786658148896, 1786658148896, NULL, 1),
('8271d8f5-4e72-4bdf-a406-060a45b51c73', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789236000000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789242379961, 1789383159912, NULL, 1),
('82d22645-752e-48c1-9baf-840944d94273', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787214945318, 'Cadeau de Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1787214953100, 1787214953100, NULL, 1),
('82f9c4db-8ab1-4ce4-b31a-2de5f3da281d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787943600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788018998724, 1788018998724, NULL, 1),
('835496e5-fb28-40fe-bc74-fb809d79c016', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1791130800000, 'Coiffure', NULL, NULL, NULL, NULL, NULL, NULL, 1791190547670, 1791190548566, NULL, 1),
('839ef9aa-8374-4822-af38-8bbd51ed0da0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788283800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788343608221, 1788364214786, NULL, 2),
('83a69927-343e-4084-8ea7-c70940c9364e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788160680000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788164337669, 1788209978548, NULL, 2),
('844e53f6-d91d-4a40-936a-ee3733c45381', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 250000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787401800000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1787430920321, 1787430920321, NULL, 1),
('84585531-d597-4df8-b87d-c7fdd27abf47', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1790031600000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790066116223, 1790076620401, NULL, 1),
('84962ca8-9e10-4d9d-9296-0d56e28d09af', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1787220289086, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1787220336406, 1787220336406, NULL, 1),
('865549d5-efe3-4244-adbc-92ae4f70d641', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788527100000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788528234879, 1788528235919, NULL, 1),
('869c8e73-f240-4015-887b-10333b0bda74', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787817660000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787906567100, 1787909331658, NULL, 2),
('882151bb-9d60-4418-931b-41ed2c14c3f3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 300000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1774998000000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1786825448501, 1786825448501, NULL, 1),
('88be230f-b765-4f0e-b165-bc69c8576d39', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 6000, 'INCOME', '97d3f709-8069-4963-8c32-874009967823', NULL, 'a8f0dd81-d119-40ba-bfd0-4ef04f87b566', 1790920891883, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790920895979, 1790946050151, NULL, 1),
('88ffbe63-bbc7-40b2-b411-2d2be037c936', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789844400000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1789862804844, 1789933926353, NULL, 2),
('8934176f-f84f-4090-b1c0-bca83b80236b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 250000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1790485200000, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1790508521339, 1790508521484, NULL, 1),
('898fe044-d10b-4768-9601-e6f25cd5b635', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787644800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787674148130, 1788705596000, NULL, 1),
('8aedc26f-c0d8-4785-8ec0-c5c7dbe545ee', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1790325360000, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1790401055414, 1790401188367, NULL, 1),
('8b37b2db-1b23-4232-ace6-1167f4def451', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790494800000, 'Couture', NULL, NULL, NULL, NULL, NULL, NULL, 1790507279772, 1790507279935, NULL, 1),
('8b405ab1-980f-4dff-b535-de1b5494e202', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 69000, 'EXPENSE', '97d3f709-8069-4963-8c32-874009967823', NULL, '0594a349-03e2-4886-b6da-e6c0dc574d15', 1790750215043, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1790750237287, 1790823757066, NULL, 1),
('8b949c4d-bf23-47c6-a9a2-164328199f9d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1786197600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786572212987, 1786572212987, NULL, 1),
('8bab083d-f0a4-4bf1-a1cb-a021fb97f60f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789718400000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725777965, 1789725778453, NULL, 1),
('8d8794a5-9af9-4b3c-a41f-3c3e42447f0d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790881200000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790889840993, 1790889841523, NULL, 1),
('8e67ec63-621e-48a6-8781-a0e6809bd644', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1789117113229, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1789117202911, 1789117578208, NULL, 1),
('8fed17e3-b135-48ca-a8ee-4b08055176e0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 37500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788681600000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788772033754, 1788772033771, NULL, 1),
('90a6fc36-7919-4927-928c-337223e1edb0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788354000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788362221844, 1788364214736, NULL, 1),
('9110f6a8-e34a-4688-9cc9-39b2f2a0fc5e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789563600000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725404294, 1789725481175, NULL, 2),
('91318579-c034-4ba9-9ba7-af65be3e6000', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 7276500, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1759350480000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739350708, 1786739350708, NULL, 1),
('91c0f2bb-3d1d-4fb7-9f3e-ec97d01aa39b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1790377200000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790507001682, 1790507001819, NULL, 1),
('91e2db2b-064f-426d-a8f6-b11e0af21f14', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 54254500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1748792880000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786722543024, 1786722543024, NULL, 1),
('91e81bb8-38b9-4e4b-a2f3-914c7d6f004a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789390800000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789401432566, 1789401432690, NULL, 1),
('9267b0bf-c145-423f-aee4-eb9cf030fba7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788109200000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788127500455, 1788127500455, NULL, 1),
('928280f8-a4f7-4cc6-88e0-0bdb76ceecd2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790168400000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790195728694, 1790196522985, NULL, 1),
('92e89fa8-c208-4fce-9471-3e557195031f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 14545400, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1746132720000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786827218366, 1786827218366, NULL, 1),
('933f2664-626f-4956-ab40-f93aa7a4c4bb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788679860000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788773574298, 1788773574377, NULL, 1),
('939179ad-5d9c-4703-87e2-a2f7463f2390', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 40000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790098860000, 'Mini Prise', NULL, NULL, NULL, NULL, NULL, NULL, 1790160129165, 1790162930003, NULL, 1),
('94c288d5-9c45-4dfe-ae84-0708b145f7b6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1772319600000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1786825408699, 1786825408699, NULL, 1),
('94ddddb1-405f-48b9-88a9-1e979dbf64b8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787664600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787674176782, 1788705596000, NULL, 1),
('954097a7-0f53-4b51-b801-a2bfb9840dbe', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790771160000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790803619154, 1790803619967, NULL, 1),
('961d0b78-8119-424b-bcb3-9f758f71b080', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 250000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790508480000, 'Achat d\'un parfum chef LI', NULL, NULL, NULL, '5ec6e079-18f4-4b28-914a-c514bbc50fd7', NULL, NULL, 1790508613673, 1790508613776, NULL, 1),
('985000fd-1ba4-4ba7-a242-34801febb706', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 17820000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1777668180000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740231114, 1786740231114, NULL, 1),
('986aff03-3e85-48ba-b042-49ad0e4084ab', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1190300, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1785345360000, 'Claude IA Pro', NULL, NULL, NULL, '298dbd25-e0da-4f8f-8a13-d7ab2436f02b', NULL, NULL, 1786908691252, 1786908691252, NULL, 1),
('9876931f-f28b-477c-95d4-ca4dc3788e9f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1790712360000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790848464780, 1790848466085, NULL, 1),
('99d9ba1d-ddd9-40f9-8bc6-1947ab779dec', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1790752860000, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1790803296980, 1790803297807, NULL, 1),
('9aa11d89-dbdb-4479-a26f-7474b3a51f32', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1787603820000, 'MyNita → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1787690285241, 1788705596000, NULL, 1),
('9b9f2e72-c4e6-4551-9d34-4b6cce200047', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790686800000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790785053249, 1790785053641, NULL, 1),
('9cf2acb0-0b26-4beb-a97a-96288f5c9612', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 382500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1790508840000, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1790509159603, 1790509159742, NULL, 1),
('9dbacb6a-86e7-4e7e-a442-8ed3e6bc38b7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 400000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1790242020000, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1790242175884, 1790242786028, NULL, 1),
('9dc52bd3-d96d-43ac-87a1-a2fd43534010', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787472180000, 'Alazi', NULL, NULL, NULL, NULL, NULL, NULL, 1787496996244, 1787496996244, NULL, 1),
('9e6fe77f-8c74-43e0-9e1a-fe37da47d547', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '6b166157-883f-44f1-a2ef-62007bea6881', 1789457280000, 'Câble type C', NULL, NULL, NULL, NULL, NULL, NULL, 1789479011682, 1789479113840, NULL, 2),
('9ea5ed00-89e7-4557-856a-68c45b78f08d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 13900, 'INCOME', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1788344517266, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788344534810, 1788348529272, NULL, 1),
('9f3a7d52-bce3-4817-b8d9-2a7c382fead6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1789321200000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1789326120889, 1789383159944, NULL, 1),
('a02d5f05-4af0-4718-b206-e6ad019c9ef4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788958800000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788959125621, 1788959136805, NULL, 1),
('a0ddfb20-cbc1-4bc3-b490-a83d76219a43', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787472000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787496910710, 1787496910710, NULL, 1),
('a0e4923a-af94-4813-a8e6-3e82e97132f8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790285700000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1790400967159, 1790401854545, NULL, 2),
('a13cc0ad-91cb-4c28-9658-31750625732e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1788008400000, '', NULL, NULL, NULL, 'a732481c-402a-4a51-87f5-e2a9ec271b99', NULL, NULL, 1788082088131, 1788082088131, NULL, 1),
('a15c8534-4ade-40de-aa99-8dc87ed6f1f5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786563360000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1786571934714, 1786571934714, NULL, 1),
('a175afa3-6b75-4514-9264-e77818633baa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 900000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1791111000000, 'Stmart Wacth serie 11', NULL, NULL, NULL, NULL, NULL, NULL, 1791190323663, 1791190335441, NULL, 2),
('a17a21e1-f1b4-45cb-8433-3b461b5b765c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787248800000, 'Le Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1787301939676, 1787301939676, NULL, 1);
INSERT INTO `transactions` (`id`, `user_id`, `amount`, `type`, `account_id`, `transfer_account_id`, `category_id`, `date`, `description`, `latitude`, `longitude`, `payment_method`, `fee_transaction_id`, `fee_type`, `receipt_id`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('a202f756-0056-4a07-bc35-7f8fb4acdecd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787299200000, 'Le petit-déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1787374903853, 1787374903853, NULL, 1),
('a2a9c32b-343e-46fd-9814-d3b90b340df6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1785348480000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1786908852896, 1786908852896, NULL, 1),
('a34508c8-3422-47bb-8d30-bba8f96b9615', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 375000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1788704760000, 'Deux Chosseurs', NULL, NULL, NULL, NULL, NULL, NULL, 1788773279680, 1788773279721, NULL, 1),
('a3cae972-5982-4fca-a3e5-8e09017b3c18', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787247060000, 'Sadaka', NULL, NULL, NULL, NULL, NULL, NULL, 1787301871950, 1787301871950, NULL, 1),
('a45b42b9-8c53-4e75-961d-7dc3ed422216', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 13096400, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1754063400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786722697180, 1786722697180, NULL, 1),
('a47e5183-4469-473a-adb7-c6980f6c6c26', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1300000, 'INCOME', '31110101-1485-4c31-a798-42d699b5de65', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1788297720000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788297791380, 1788348529187, NULL, 2),
('a48f2e01-a9a7-4a8f-8298-4eb2b106f307', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789477200000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789478268642, 1789478268759, NULL, 1),
('a4c4cc43-4840-449a-918f-64361bf2cf42', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'INCOME', '31110101-1485-4c31-a798-42d699b5de65', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1790241672226, 'Cadeau de ma part', NULL, NULL, NULL, NULL, NULL, NULL, 1790241729613, 1790242786025, NULL, 1),
('a52cad99-defb-46fc-96b8-5c0793a52e6b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a51ae322-6b10-4851-a566-1a0fb38a4a91', 1788089460000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788127156286, 1788127170767, NULL, 2),
('a5664b92-b636-422d-b45b-e7b3848a7ed0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789200000000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789214399428, 1789214638552, NULL, 1),
('a67ab882-7434-4644-ad57-d132f646ac34', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1789147800000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1789214470185, 1789214638530, NULL, 2),
('a710b28c-8060-43b8-b959-d338eabcf89f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787338860000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787375191293, 1787375191293, NULL, 1),
('a732481c-402a-4a51-87f5-e2a9ec271b99', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788008400000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788082087859, 1788082087859, NULL, 1),
('a739dde6-f600-4bee-9ca4-759e44dfbabe', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1788675780000, '', NULL, NULL, NULL, '489b516a-6690-4701-b69a-4832cab9df02', NULL, NULL, 1788773088235, 1788773088203, NULL, 1),
('a7caa3ac-2cf1-4397-8ed5-8fbce53eca75', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786537800000, '', NULL, NULL, 'CASH', NULL, NULL, NULL, 1786571788496, 1786571788496, NULL, 1),
('a823fe31-fe1d-4076-8978-d352018e9d59', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 556900, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1790610240000, 'innovateurspaix.org', NULL, NULL, NULL, '3bb53764-a397-43ea-9e30-2db4adaf8fa3', NULL, NULL, 1790846915671, 1790846916841, NULL, 1),
('a89f8914-ff11-4e55-9df9-fa72ac247509', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 75000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1790795760000, 'CleanMan (Savon pour Homme)', NULL, NULL, NULL, NULL, NULL, NULL, 1790846252602, 1790846253788, NULL, 1),
('a9902876-3407-4dce-89d2-cb9ed6e4af58', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787764200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787818144074, 1787818144077, NULL, 1),
('aa0eef6c-a4c9-4f44-97ae-b38bde6f427f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790082000000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790160054548, 1790162930001, NULL, 1),
('aacb3c10-2efa-4a25-90df-66db63ca0d04', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 13779300, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1777666680000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738747296, 1786738747296, NULL, 1),
('ab2854a5-2f7a-4267-8aa6-d4950cdeb606', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787425500000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787438846522, 1787438846522, NULL, 1),
('abacf1d2-06cb-4a09-b196-031d8891a582', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787733840000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787748279795, 1787748279802, NULL, 1),
('abfde614-458c-4bbc-a8d9-788cccc7a074', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787315400000, 'Le déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1787375010511, 1787375010511, NULL, 1),
('ac4eba3c-028c-4dba-b02d-2c49741dfc19', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1787900400000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1787910688225, 1787910688225, NULL, 1),
('ad163be2-e105-451f-84a5-7e357dc01d83', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1789203660000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1789243534987, 1789243789972, NULL, 1),
('ade495ea-82ee-4ecb-ba91-64e5851e9463', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790204400000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790261482973, 1790261485923, NULL, 1),
('ae18ee9c-3785-41c9-83e7-eb783be3db9b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'INCOME', '31110101-1485-4c31-a798-42d699b5de65', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1790260980000, 'Payement pour la tontine (Pour moi)', NULL, NULL, NULL, NULL, NULL, NULL, 1790261136550, 1790261286566, NULL, 3),
('aeeb4c71-b599-4736-b354-abb8ce0287f3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788595260000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788646492718, 1788646599258, NULL, 2),
('aefe98ae-c057-4f1a-8ee6-3ee9254e4234', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787387400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787395113785, 1787395113785, NULL, 1),
('af2c67ab-2d96-4af5-b41c-017a3b5d932c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790409600000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790506188726, 1790506222404, NULL, 2),
('b0a2c922-1f38-4bbe-ae4f-e84930d0ca81', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1791129180000, 'Abdoul Rachid NinoTech', NULL, NULL, NULL, NULL, NULL, NULL, 1791190467983, 1791190480975, NULL, 2),
('b0f70324-7df0-46f6-bee8-7be0f559238b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788717600000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788772058527, 1788772058387, NULL, 1),
('b11f7661-2de0-447a-918d-63b26f852db3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8e4f9cf0-c631-489d-be3a-757f533fb3f1', 1788474000000, '', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788473114235, 1788515862925, 1788515862925, 2),
('b1c33dab-39c9-474f-b98d-1253a9933013', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1790241300000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790262960682, 1790265167716, NULL, 1),
('b216ee67-32e8-44f8-831f-5649d4fea1e2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 288000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1785340500000, 'Abonnement ChatGPT Go', NULL, NULL, NULL, 'c90f083a-944f-4ccd-8019-4257b9bd1b4b', NULL, NULL, 1786908048295, 1786908048295, NULL, 1),
('b2bbc37a-266e-4e35-9069-dba6ec29b29c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788111600000, 'Coco', NULL, NULL, NULL, NULL, NULL, NULL, 1788164302224, 1788164302231, NULL, 1),
('b2e0baa2-f7b4-461f-9d88-51be64c2fa96', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1790937180000, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1791023645202, 1791023710429, NULL, 1),
('b306a300-8e1c-4a06-bdaf-8da73a59eed0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1791187200000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1791190874256, 1791190875234, NULL, 1),
('b335fbcf-8631-4310-a0b1-631f23ed7d9e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 13800000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8ff6b4d1-2775-48d0-93a7-dda7b93b5536', 1788247800000, 'Qwiper', NULL, NULL, NULL, NULL, NULL, NULL, 1788256611675, 1788258070535, NULL, 2),
('b3981e29-1234-461a-8cb9-22b8569058b6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787248860000, 'Abdoul Karim (Agadez)', NULL, NULL, NULL, NULL, NULL, NULL, 1787302043723, 1787302043723, NULL, 1),
('b3e54493-681f-49d2-b371-64682fcbd0f6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1550000, 'TRANSFER', '86f251b7-2294-4d28-b169-debcf9ed0b77', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 1790665500000, 'Amanata → Visa Amanata', NULL, NULL, NULL, 'b893240e-061d-4a88-ae67-afee42dbb3cc', NULL, NULL, 1790847216166, 1790847872617, NULL, 2),
('b4535d8a-b734-4109-bed2-1d58083ddee8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787922000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788018937274, 1788018937274, NULL, 1),
('b4ac2f81-aea0-4e02-a0f9-8dd20597e701', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1789132640519, '', NULL, NULL, NULL, NULL, NULL, NULL, 1789132656132, 1789132666513, NULL, 1),
('b4b78cd1-d26b-4c19-9013-aa5ff30f57c5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1789858800000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1790066008826, 1790066033133, NULL, 1),
('b4fba8db-cf4c-4e45-9b42-f5d832a995cb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790967600000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1791023476043, 1791023710426, NULL, 2),
('b613bf8a-72a6-4da0-921d-496cd62965d5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789650000000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725651651, 1789725653185, NULL, 1),
('b699a979-9116-45b7-9e0e-acc204387c07', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790841600000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790848764695, 1790848766081, NULL, 1),
('b6a05816-0d5a-4ad7-b0cc-caebd049fd43', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1789293480000, 'Coiffure', NULL, NULL, NULL, NULL, NULL, NULL, 1789325950122, 1789383159929, NULL, 1),
('b7c9aaf9-65de-4687-acb0-e2f21cd4a727', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790668800000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790673219344, 1790673379106, NULL, 1),
('b84b95f1-af18-48b6-86be-d742e4ffab4f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 1787229960000, 'Espèces → Wave', NULL, NULL, NULL, NULL, NULL, NULL, 1787223963336, 1787223963336, NULL, 1),
('b892c1a0-e2a0-42e8-904a-7e61ea55259c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1786825114785, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1786825153258, 1786825153258, NULL, 1),
('b893240e-061d-4a88-ae67-afee42dbb3cc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790665500000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790847215680, 1790847872116, NULL, 2),
('b8d2022d-a717-4115-9145-95c9f0c568aa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1790713320000, 'Envoie a Djbo', NULL, NULL, NULL, '70a262ff-b3e2-46de-b763-bb6e910f4fec', NULL, NULL, 1790803429395, 1790803430222, NULL, 1),
('b8d771d5-be67-4294-b9a4-9effdc6710cb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1788528180000, 'Espèces → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1788528273077, 1788528289255, NULL, 2),
('b8e6df5d-5770-46a4-9197-9d0e0567c420', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1787211000000, '', NULL, NULL, NULL, NULL, 'SERVICE', NULL, 1787214157347, 1787214157347, NULL, 1),
('b93e850f-3280-474c-9a6a-6cafde0e5a96', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788210420000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788214045117, 1788214045117, NULL, 1),
('b941d467-e521-4fd5-8666-e9098a55c9b4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 520000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787603580000, 'Boubé', NULL, NULL, NULL, '98d09c89-8bd6-4ec9-8f69-4264f73ddf88', NULL, NULL, 1787690062836, 1788705596000, NULL, 1),
('b97accc2-8302-4b7a-8377-c2f4e4307828', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 75000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788528420000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788528521814, 1788528522803, NULL, 1),
('b9be380c-84ba-4a5d-b299-972193070b87', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1786726800000, 'MyNita → Espèces', NULL, NULL, NULL, 'bbaf88e8-08a5-4f15-936a-bf948c0d3cdf', NULL, NULL, 1786741002232, 1786741002232, NULL, 1),
('ba4f7794-53f3-445f-909a-a852070dae75', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1789756200000, 'Savon', NULL, NULL, NULL, NULL, NULL, NULL, 1789762119688, 1789763229515, NULL, 2),
('bbaf88e8-08a5-4f15-936a-bf948c0d3cdf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1786726800000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1786741002238, 1786741002238, NULL, 1),
('bc182ce0-cdff-4639-b1b2-24886664d59f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 250000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1790377200000, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1790507930961, 1790507931078, NULL, 1),
('bce58b7f-1e42-49fa-9078-48cf261d07d5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788976800000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788979871726, 1788979872436, NULL, 1),
('bd1c908c-f9e3-4861-9a17-0ec8d80d0f35', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 70000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1787396700000, 'Patte dentifrice', NULL, NULL, NULL, NULL, NULL, NULL, 1787438738900, 1787438738900, NULL, 1),
('bd93b1d2-56d7-480e-a4c7-1479fb83d83f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1250000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1787211000000, 'Connexion internet', NULL, NULL, NULL, 'f64161ff-19f4-4fdf-a430-d7878e57a34c', NULL, NULL, 1787213966148, 1787213966148, NULL, 1),
('bdaf7d11-d89a-465d-a056-9e63b9ce69a7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '06a7915c-05f4-40ba-a37f-fd3544ae4339', 1790377200000, 'Emprunt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790507373325, 1790507373403, NULL, 1),
('bdd46c02-3200-4439-93ce-772e45f94370', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787425380000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787438638239, 1787438638239, NULL, 1),
('be2723b1-7874-42e5-bad8-e173915519d5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788033600000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788070764496, 1788277955068, NULL, 2),
('be502dfe-d824-4e95-9f2d-50abbc73f8bd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1791014700000, 'Boube', NULL, NULL, NULL, NULL, NULL, NULL, 1791189629673, 1791189630579, NULL, 1),
('be5f986a-1c7f-4f0e-bb77-ac9a3385e485', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788338340000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788341993413, 1788348529170, NULL, 1),
('be67a57c-031e-4165-9050-8e413d9fde60', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1788289200000, 'Tisu chez LI', NULL, NULL, NULL, 'cc789651-69b0-47e8-8b9b-0621d9f3035c', NULL, NULL, 1788298096703, 1788298096703, NULL, 1),
('bf06a1cb-9ae4-4fe1-8118-4151db30c751', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1786541400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786572246852, 1786572246852, NULL, 1),
('bf6f4dfe-a223-419f-89ce-1f37cf8ad397', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a51ae322-6b10-4851-a566-1a0fb38a4a91', 1787771400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787818276438, 1787818285021, NULL, 2),
('bfdd7a12-10d9-4cd8-98a2-de03e592ef6b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789995600000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790065668359, 1790066033130, NULL, 1),
('c006d37e-6932-4722-a447-bf283f9612d1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 400000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1787076000000, 'MyNita → Espèces', NULL, NULL, NULL, 'd1736c5d-cec9-4f04-a68f-dd49f95992d1', NULL, NULL, 1787090141097, 1787090141097, NULL, 1),
('c108bdb6-7765-4956-ba55-f193e678f5aa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22027500, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1775076120000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740171951, 1786740171951, NULL, 1),
('c133570c-295e-4fd5-8bcd-c8e93b6855ed', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 600, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787828400000, 'Ras', NULL, NULL, NULL, NULL, NULL, NULL, 1787846598498, 1787846641006, 1787846641006, 2),
('c1ad0a6d-45a2-4c59-a245-1777d585795b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 35000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789632000000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725591477, 1789725592782, NULL, 1),
('c209db46-40a4-4f4a-b4eb-510413c4d7d9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1768950000000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1786824974185, 1786824974185, NULL, 1),
('c21b9478-4534-4161-b6d4-1d2b0eb13b80', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786993200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787010592098, 1787010592098, NULL, 1),
('c3be6751-8df7-4ecb-8bbf-43f1121773f3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787733000000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787818391591, 1787818391593, NULL, 1),
('c3e14fd9-23b7-451d-a5c7-1356eca8f95f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1791014400000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1791023764800, 1791024249063, NULL, 1),
('c425daea-1384-4aa3-a57f-6c72277510cf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786798800000, 'Dille', NULL, NULL, NULL, NULL, NULL, NULL, 1786827637601, 1786827637601, NULL, 1),
('c5171295-ac3b-49cb-a948-f9b0f871b0af', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1788723000000, 'Savon', NULL, NULL, NULL, NULL, NULL, NULL, 1788773510370, 1788773510416, NULL, 1),
('c566f6df-c0c8-493c-9ef1-37d1c6fd151d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 600000, 'TRANSFER', '86f251b7-2294-4d28-b169-debcf9ed0b77', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 1788293400000, 'Amanata → Visa Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1788343865082, 1788348529232, NULL, 1),
('c56e7995-3323-4319-addb-ea40f815213b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789671600000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725675013, 1789725676550, NULL, 1),
('c595675a-ed06-4d2f-9f68-9418ddd2810d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '31110101-1485-4c31-a798-42d699b5de65', NULL, '82c2282a-f15f-4976-b380-e1a6331280d8', 1788004800000, '', NULL, NULL, NULL, '30de2572-18b5-4f11-a22b-0b783f2ccdf6', NULL, NULL, 1788081875705, 1788081895196, NULL, 2),
('c5fbbbf1-efdd-4918-81d8-9becd467ca50', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 1787214649077, 'Espèces → MyNita', NULL, NULL, NULL, NULL, NULL, NULL, 1787214759805, 1787214759805, NULL, 1),
('c60b3e08-93cc-4c68-b2e7-b0b9a54e6a48', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1790204400000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1790334665670, 1790334666426, NULL, 1),
('c6694ae0-9876-4ec7-8073-0f27e42897c7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788249600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788258049717, 1788258049717, NULL, 1),
('c6ae753b-1bb9-4b0b-af6e-cc41fd798c4c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1786971600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786978570297, 1786978570297, NULL, 1),
('c7bb2a45-4479-4151-b9fd-f6e3534b284f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1787828400000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1787849694975, 1787873496579, 1787873496579, 2),
('c90f083a-944f-4ccd-8019-4257b9bd1b4b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1785340500000, '', NULL, NULL, NULL, NULL, 'BANK', NULL, 1786908048302, 1786908048302, NULL, 1),
('c926a952-5a5c-478b-bdf4-5085ed190ca0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 4010000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1780346640000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786740339729, 1786740339729, NULL, 1),
('c93e21c6-76c8-4991-b427-db157441b2aa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1790377200000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1790506328271, 1790506328355, NULL, 1),
('c9730aaf-5fa6-4666-9233-36c219ee75c9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787943660000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788019038375, 1788019056635, NULL, 2),
('c9b3d833-ce07-45e3-82d3-d578c57f9f57', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790449200000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790506461193, 1790506472559, NULL, 2),
('ca1b1e6e-05d1-40e7-aae5-b38c798bdc57', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789027200000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789029355004, 1789029359856, NULL, 1),
('ca3e4b3b-c5ad-4e2c-8fa8-234f9e681315', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789758000000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1789762151418, 1789763229517, NULL, 1),
('ca5707a7-31c8-4e14-88d6-649e72e038a1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790794800000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790803135475, 1790803136364, NULL, 1),
('cbee7bd9-e765-4098-a76e-9869346f743c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1787603580000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1787690240748, 1788705596000, NULL, 1),
('cbf91737-46eb-4027-b824-fb9b27a23e2a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 400000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '06a7915c-05f4-40ba-a37f-fd3544ae4339', 1788217200000, 'Emprunt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790241928194, 1790242786026, NULL, 1),
('cc20f68f-04d9-4f7e-b952-977f7ee05051', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 650000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787396400000, 'Achat de Batterie de Moto', NULL, NULL, NULL, NULL, NULL, NULL, 1787429023935, 1787429023935, NULL, 1),
('cc75e586-32f5-466b-bd90-d3b45afbe77e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 600000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1788217200000, 'Connexion internet', NULL, NULL, NULL, NULL, NULL, NULL, 1788264226567, 1788264226567, NULL, 1),
('cc789651-69b0-47e8-8b9b-0621d9f3035c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788289200000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788298096502, 1788298096502, NULL, 1),
('cce72288-23d4-4553-8476-8c30f275e66b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787733840000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787748305282, 1787748305285, NULL, 1),
('ccf4800f-3bc1-4a84-927e-aa44b1bf377e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 70000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788019200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788070589141, 1788277954982, NULL, 2),
('cf8316b2-af3f-4d89-b74f-61197cf24b1f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10806500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1780345140000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738797035, 1790401854546, NULL, 2),
('cfc2e234-090b-47af-948e-3cde246f21b7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1788423480000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790263109890, 1790265167718, NULL, 1),
('d1736c5d-cec9-4f04-a68f-dd49f95992d1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1787076000000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1787090141104, 1787090141104, NULL, 1),
('d1c4a44c-ddfd-4a9d-9636-5f4d9c45d91c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1789149180000, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1789116858143, 1789117165022, NULL, 1),
('d265c1a7-3733-4b4a-893d-03522784c3c0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1789030184297, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1789030203922, 1789243790017, 1789243790017, 2),
('d2912044-146b-4f72-b7cf-771398034611', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788341940000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788342027181, 1788348529176, NULL, 1),
('d46b050a-e236-40f4-9a6e-ee6cda400ee1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787685000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787689794578, 1788705596000, NULL, 1),
('d5410235-e457-482b-be5b-38f888486037', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 150000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787396460000, 'Réparation des lampes du moto', NULL, NULL, NULL, NULL, NULL, NULL, 1787430725011, 1787430725011, NULL, 1),
('d66b68c3-1d21-4031-9644-7ba3688781b2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 300000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790357160000, 'Vidage de moto', NULL, NULL, NULL, NULL, NULL, NULL, 1790400488415, 1790401188362, NULL, 1),
('d66ffeee-e2e9-482c-99be-7166cbb884ba', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790496000000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790507194664, 1790507194760, NULL, 1),
('d68ebadb-d48e-4854-a9d7-0f62a2b51f3b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788206400000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1788210452959, 1788210475405, NULL, 2),
('d699723a-aba6-4e40-b28e-255b7711d251', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 400000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1788254760000, 'Tissus', NULL, NULL, NULL, NULL, NULL, NULL, 1790242014723, 1790242786027, NULL, 1),
('d6d986b6-bd71-42ff-8045-9e009e0ac839', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'INCOME', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '70307aa9-3454-4864-b81a-a423354532ee', 1785343560000, '', NULL, NULL, NULL, '3b22d925-b1d8-42a4-a8fe-925a8f6144cf', NULL, NULL, 1786908354674, 1786908354674, NULL, 1),
('d7166782-3bd4-4c4f-8acf-2e458a5e5d24', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 150000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1791098400000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1791190921744, 1791190922736, NULL, 1),
('d78aed90-ed01-4762-a238-d391db366aa1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790622060000, 'Rido', NULL, NULL, NULL, NULL, NULL, NULL, 1790673357318, 1790673379107, NULL, 1),
('d7a2485e-1836-4d7e-9401-8f66ecc2a1d4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790150400000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790160797366, 1790162930011, NULL, 1),
('d89ae32f-57af-4dc6-b251-14bdb1a2cef1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 44700, 'TRANSFER', '63a53de8-284c-4e22-8e32-6f29ea237930', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1789204080000, 'Visa Amanata → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1789243748788, 1789243790004, NULL, 1),
('d92e5dda-bf63-4716-8c4c-d9d2b72d1eaf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 250000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788296760000, 'Dige', NULL, NULL, NULL, NULL, NULL, NULL, 1788297155934, 1788297155934, NULL, 1),
('d970f8c4-0b7e-486c-a921-c92f0080e17b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787079600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787089983231, 1787089983231, NULL, 1),
('d9c1ae56-0b67-4eab-90aa-f0f75ef9d8c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786624980000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786657451211, 1786657451211, NULL, 1),
('d9c9aae6-eede-4085-8d20-54b6e9859396', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 282300, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1788534720000, 'ChatGPT Go', NULL, NULL, NULL, 'f7170f4f-0622-4f46-b5c4-727b6987e3d0', NULL, NULL, 1788534837907, 1788534911809, NULL, 2),
('d9fc6cbb-a681-4f2e-9f26-72262c26029c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790946358144, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790947256941, 1790947415820, NULL, 1),
('da2258d2-dbd4-4cdd-9354-cdc8f18e39d3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1788012000000, 'Espèces → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1788019260510, 1788019260510, NULL, 1),
('da45f1ee-bcb4-46ce-8a3f-a074e7191273', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1777669980000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828459317, 1786828459317, NULL, 1),
('da6ed927-567d-4d74-bd61-e615d0005b2f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788354120000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788362278219, 1788448214349, NULL, 3),
('dac610c0-3478-476f-a786-c85a1d6f3001', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790240880000, 'QWIPER', NULL, NULL, NULL, NULL, NULL, NULL, 1790262535374, 1790265167713, NULL, 1),
('dadf6e1a-1636-4de8-b52f-c4c63502a829', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 17779600, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1775074560000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738660907, 1786738660907, NULL, 1),
('daeece49-136b-4a58-99b4-9a2d545bf1d7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1791140100000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1791191715999, 1791191716938, NULL, 1),
('dc104f96-41ac-485a-aa3f-a94a359882c5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 1787410860000, 'Espèces → Mariage', NULL, NULL, NULL, NULL, NULL, NULL, 1787438353776, 1787438353776, NULL, 1),
('dc3d631a-5c03-49dd-9ffd-86a90823350a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 107707500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1764619740000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738218926, 1786738218926, NULL, 1),
('dc886272-b113-4144-9afc-ce411b2bb563', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787429100000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787438952450, 1787438952450, NULL, 1),
('ddaf50b0-9b9e-468c-9892-8311487ebf82', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 125000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1790929560000, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1790933219723, 1790933220063, NULL, 1),
('ddc71389-391a-4d3a-b800-24b58e6af136', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1789221600000, 'Djbo', NULL, NULL, NULL, NULL, NULL, NULL, 1789243097442, 1789243790009, NULL, 1),
('ddce13e4-e777-4292-9b78-a6db8ab5960e', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 15000, 'INCOME', 'a698ded3-c4ec-41a7-b0ef-141c756add1c', NULL, '95679e67-8988-47b7-9b8a-938882f3fef8', 1790828977435, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790829034631, 1790835052310, NULL, 1),
('de23a44c-910a-4dba-b04c-17e60a488bc5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 650000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788343743690, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788343759069, 1788348529227, NULL, 1),
('de3e97ea-0553-421e-8433-02a999535565', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790708400000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1790785142911, 1790785143495, NULL, 1),
('de7e2388-4f13-410b-9748-0ab8a553c8aa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 152500, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788127260000, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1788127311197, 1788127311197, NULL, 1),
('df0f7b2a-1771-4816-b584-2692479d8a49', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788164340000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788164386087, 1788209966485, NULL, 2),
('df178bf8-39bb-4596-8e81-1971f4659980', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 90000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788694380000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1788798869233, 1788798986440, NULL, 1),
('df255eb6-471e-46b8-ac32-47a1c9d4166e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c6262555-d57c-4cc3-a1d5-6d682eb4022d', 1790262222808, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790262232563, 1790265167711, NULL, 1),
('df62e1ae-8803-44be-b28f-5f89772c634b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1788539700000, 'Test', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788555506241, 1788555527995, 1788555527995, 2),
('df6ba267-9968-4b2b-ae60-b8d529fae2a9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 950000, 'TRANSFER', '86f251b7-2294-4d28-b169-debcf9ed0b77', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, 1790610000000, 'Amanata → Visa Amanata', NULL, NULL, NULL, '13b25353-2c7b-4492-9dba-62ea728b1c7d', NULL, NULL, 1790846695506, 1790847012992, NULL, 3),
('dfd973ba-7875-4ec9-82db-9fa6f2a4d86b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788296640000, 'Cadeau de Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1788296742076, 1788296742076, NULL, 1),
('dff1dd0c-89f1-46a0-803b-cf104b57eeef', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787472060000, 'Ridouane', NULL, NULL, NULL, NULL, NULL, NULL, 1787496961470, 1787496961470, NULL, 1),
('e1096d2a-66a1-4204-8bee-8859a696bf3c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 80000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1791054000000, 'Dîner', NULL, NULL, NULL, NULL, NULL, NULL, 1791190216140, 1791190217162, NULL, 1),
('e164a7a6-e27f-49f1-b301-d3e31908a3ae', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789977600000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790065630989, 1790066033128, NULL, 1),
('e20748e4-dce2-40e7-bc6d-ee37bd0fd5f3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787403600000, 'Alazi et Rido', NULL, NULL, NULL, NULL, NULL, NULL, 1787438786740, 1787438786740, NULL, 1),
('e23b0347-9b6b-429c-9a67-734737ca62b8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 75000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1791118800000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1791190393571, 1791190394561, NULL, 1),
('e25c3560-6b29-4f24-8284-90ce035d2cdf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1789321200000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1789326151324, 1789383159956, NULL, 1),
('e277ea66-2185-48f9-af24-d36596de8590', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1787764800000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1787818189092, 1787818189095, NULL, 1),
('e2eeb172-63b7-42b4-ba60-d22b3b9d9d26', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789149600000, 'Dîner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789478246438, 1789478246640, NULL, 1),
('e301d438-b99b-4961-9bf7-bcd681ab38f6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1790377200000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790507034492, 1790507034570, NULL, 1),
('e30f989d-2818-49af-b0d3-f0796e935e66', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 500000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1788704820000, 'Une chemise', NULL, NULL, NULL, NULL, NULL, NULL, 1788773325676, 1788773325589, NULL, 1),
('e351d46b-d3f6-4c46-8927-8051e1213362', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1789293540000, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1789326034310, 1789383159938, NULL, 1),
('e4bee6d3-674b-4434-87e9-5d623eab7e73', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 2000000, 'INCOME', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 'a8a6a002-3519-4816-8c11-09486c31d655', 1767301620000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786828109664, 1786828109664, NULL, 1),
('e4e28a25-307c-469e-8506-cdbbb6db436d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 130000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1787214426711, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787214442288, 1787214442288, NULL, 1),
('e50e5924-6432-4dca-84a0-2075cc4b5e38', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788268800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788296644887, 1788296644891, NULL, 1),
('e51c5b02-0840-48d0-b41b-fb67b4cc0661', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1788298587005, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1788298607169, 1788443914000, NULL, 1),
('e5bea844-57b2-4f93-9d53-bf9c4a2e7efd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 330000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1788941760000, 'RAS', NULL, NULL, NULL, NULL, NULL, NULL, 1788941863264, 1788941863705, NULL, 1),
('e5e34c76-ff1d-47dc-852e-d49c46ba480f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787124600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787127992957, 1787127992957, NULL, 1),
('e5f659e7-c29e-4eb4-828f-3ea784876f60', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787558400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787560693900, 1787560693900, NULL, 1),
('e6f22198-cc6a-4543-9838-e2e145fdedc6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'be4274ce-0aa2-4b32-aebc-362efa946023', 1790523420000, 'Chaussures arrange', NULL, NULL, NULL, NULL, NULL, NULL, 1790584794562, 1790585114073, NULL, 1),
('e75a2435-e525-4f39-9d94-e0e586edb1c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1190400, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1790847180000, 'Claoud Pro', NULL, NULL, NULL, '808bf5bf-82ee-4049-b01e-553b8f525dc1', NULL, NULL, 1790847310944, 1790847312139, NULL, 1);
INSERT INTO `transactions` (`id`, `user_id`, `amount`, `type`, `account_id`, `transfer_account_id`, `category_id`, `date`, `description`, `latitude`, `longitude`, `payment_method`, `fee_transaction_id`, `fee_type`, `receipt_id`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('e77b8963-3928-482b-ae0c-0dab7f23ab25', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1788719400000, 'Coco', NULL, NULL, NULL, NULL, NULL, NULL, 1788773456411, 1788773456361, NULL, 1),
('e7e6db0a-3432-4acd-9c4b-3bd1b5105038', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 13870000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8ff6b4d1-2775-48d0-93a7-dda7b93b5536', 1790233920000, 'Qwiper', NULL, NULL, NULL, NULL, NULL, NULL, 1790241177583, 1790242786018, NULL, 1),
('e8c1ff5f-abf5-45f9-8d50-6901c920e64e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786795260000, 'Dille', NULL, NULL, NULL, NULL, NULL, NULL, 1786827696801, 1786827696801, NULL, 1),
('ea2e4e4f-10a9-4203-8625-d523e1fedc59', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1789478670071, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1789478712502, 1789479113830, NULL, 1),
('ea343ff9-08b0-49e3-ba6e-cd353638a42d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789113600000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1789116399218, 1789116759275, NULL, 1),
('eaa0aa32-568d-450f-8159-c81a01c128cd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', '58d0b718-d6c5-46ed-b534-06366e1cf252', NULL, 1790244360000, 'Espèces → Mariage', NULL, NULL, NULL, NULL, NULL, NULL, 1790244595045, 1790244598051, NULL, 1),
('eb070c44-b1c7-4b48-a892-deca6d8b62a3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 75000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787643000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787674101272, 1788705596000, NULL, 1),
('eb200034-9c6a-43b2-9039-f019651ca6b3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '843152ec-f4d6-4e84-9a79-f72eca40dee8', 1790625600000, 'Remboursement de prêt reçu', NULL, NULL, NULL, NULL, NULL, NULL, 1790673021838, 1790673379104, NULL, 1),
('ed886315-3c9c-4299-9dea-a0070cde9de7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788111000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788127573001, 1788127672344, NULL, 2),
('edc125f2-4d33-4f6f-819d-72e4796a3ada', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000000, 'TRANSFER', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, 1790324520000, 'Espèces → Wave', NULL, NULL, NULL, NULL, NULL, NULL, 1790400268316, 1790505995894, 1790505995894, 2),
('ee655af7-55c6-4772-a6f5-167f7cebe4b2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '8e4f9cf0-c631-489d-be3a-757f533fb3f1', 1787828400000, 'Test frais', NULL, NULL, NULL, 'c7bb2a45-4479-4151-b9fd-f6e3534b284f', NULL, NULL, 1787849695711, 1787873497517, 1787873497517, 2),
('eea3a5c2-119a-4bea-bae2-cf413080703e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787763600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787818110344, 1787818110348, NULL, 1),
('eebf0cc4-0cee-49d2-b67d-2384a2876651', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1785353940000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786911333693, 1786911333693, NULL, 1),
('ef43e9fa-d696-4f81-8ae3-4941f82c357c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790523600000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790584868625, 1790585114076, NULL, 1),
('f06e33bb-d00e-425a-9bb7-cdaf20dbb5b0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd80fe1c6-558a-4cc3-a1d2-ae4cc39ab1c3', 1789407034843, 'Remboursement d\'emprunt', NULL, NULL, NULL, NULL, NULL, NULL, 1789407049955, 1789478227648, 1789478227648, 2),
('f091cec4-1b0b-47c1-bb5e-de10fa825b67', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790928000000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790933146701, 1790933147083, NULL, 1),
('f0fe6a24-770f-4e0a-888d-0a0382284530', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790325180000, 'Maman', NULL, NULL, NULL, NULL, NULL, NULL, 1790400887920, 1790401188365, NULL, 1),
('f15114b7-db72-4380-b6f1-6e73264a0b84', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1787247000000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1787301806602, 1787301806602, NULL, 1),
('f173f3c7-7f0c-4027-8d3c-86c0856c971e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790538060000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1790584922014, 1790585114436, NULL, 1),
('f2152388-6bab-4e1b-a699-a35162ac86ef', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790357400000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1790400626275, 1790401188363, NULL, 1),
('f27effe6-3c9a-40bf-a8f6-9af97253a48d', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 100000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1789752600000, 'Carburant', NULL, NULL, NULL, NULL, NULL, NULL, 1789762077500, 1789763229510, NULL, 1),
('f2c0980b-b7c7-44b9-805a-23168123a166', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1200000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '09404dda-c202-4f95-8a82-8836ff7f3bfb', 1790236200000, 'Connexion internet', NULL, NULL, NULL, NULL, NULL, NULL, 1790241240860, 1790242786021, NULL, 2),
('f2cc4f28-5541-41f2-bcc1-2f5f45552020', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788786000000, 'Déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788789480406, 1788789484065, NULL, 1),
('f30b801c-b553-4fa0-a904-77236fa91325', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786737600000, 'Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1786741264636, 1786741264636, NULL, 1),
('f32dfed9-d067-4b30-adba-ea359f15fc25', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3141000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1762029000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739662439, 1786739662439, NULL, 1),
('f3c7943b-33e5-4572-bbd2-96fbaae5c7fd', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790600400000, 'Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790671853209, 1790673379091, NULL, 2),
('f431a8a5-f82b-4b43-8294-357707fc8dc4', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 250000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1790454000000, '', NULL, NULL, NULL, '1ab74ccc-ec2b-4815-bb5f-44f8f31cdaa3', NULL, NULL, 1790508088480, 1790508747626, NULL, 2),
('f48d0376-f497-4ba7-8546-e7e13545e4a8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 11550000, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1790281440000, 'Héritage', NULL, NULL, NULL, NULL, NULL, NULL, 1790328357077, 1790328357806, NULL, 1),
('f52bc866-93ad-4d83-a609-16070504e881', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 5000, 'EXPENSE', 'a698ded3-c4ec-41a7-b0ef-141c756add1c', NULL, '0557e9d8-9a1a-4f4a-8e41-ac7af15b3032', 1791130036641, '', NULL, NULL, NULL, NULL, NULL, NULL, 1791130066711, 1791181009133, NULL, 1),
('f5cbd041-8cb1-4d05-b834-5a54edaba09e', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10700, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1790848140000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790848278802, 1790848317545, NULL, 3),
('f5d2cbcf-234a-4e92-aa39-7f444e985a91', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790343840000, 'Oriba + Œufs', NULL, NULL, NULL, NULL, NULL, NULL, 1790400361734, 1790401188356, NULL, 3),
('f5d9268c-7025-41a9-bdd9-b7f77defa917', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 1790608800000, 'Réparation de Moto', NULL, NULL, NULL, NULL, NULL, NULL, 1790672201267, 1790673379100, NULL, 1),
('f6925776-c7d2-483e-95e7-523f7028edbc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 75000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1790775300000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1790803168195, 1790803169078, NULL, 1),
('f6b2bcef-7624-48d6-9a5b-00231e0be59a', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 3000000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790945645996, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1790945661076, 1790947415825, 1790947415825, 2),
('f70356ba-9d3e-4ad6-8c66-b06455c291ab', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788595200000, 'Petit-déjeuner', NULL, NULL, 'CASH', NULL, NULL, NULL, 1788644924129, 1788646402105, NULL, 1),
('f70a3b59-2f2c-4039-b96b-c49987e8b1cc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788595380000, 'Rido', NULL, NULL, NULL, NULL, NULL, NULL, 1788646550139, 1788646599270, NULL, 2),
('f7170f4f-0622-4f46-b5c4-727b6987e3d0', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 60000, 'EXPENSE', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1788534720000, 'Frais et commissions', NULL, NULL, NULL, NULL, 'BANK', NULL, 1788534837664, 1788534911738, NULL, 2),
('f75fc7af-4345-40c2-8c57-c3a08ae58ff1', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 15000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1789045980000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1789049704274, 1789049919981, NULL, 1),
('f7ee14b2-bed7-410d-a846-cd00f902df8b', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 50000, 'EXPENSE', '97d3f709-8069-4963-8c32-874009967823', NULL, '0350b2f0-d812-4120-9b5d-aa8e56c7ea92', 1790581177648, '', NULL, NULL, NULL, '68e2ff3b-b825-4091-b354-88c7bd743145', NULL, NULL, 1790581214530, 1790651994698, NULL, 1),
('f8ac5826-174b-485c-9bea-4dfb529375cb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'c71e34f1-59e9-48e1-9976-aa5c5bb1c22e', 1790013000000, '', NULL, NULL, NULL, NULL, 'TRANSFER', NULL, 1790160714662, 1790162930009, NULL, 1),
('f8f8f394-1dfe-4c21-8e65-0b309219ef33', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'a146399c-2ebb-40b2-8b01-2073528b1898', 1788084000000, 'Coiffeur', NULL, NULL, NULL, NULL, NULL, NULL, 1788127092767, 1788127092767, NULL, 1),
('f91fbda1-3638-4946-8c37-bd450c320bab', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790064000000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790160033535, 1790162929996, NULL, 1),
('f9225a99-c7e6-43eb-a00a-58ad2e488000', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1790755200000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1790803112382, 1790803113190, NULL, 1),
('f92f19b5-f11a-4da2-9c42-89c96ac646c2', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 22000, 'EXPENSE', '63a53de8-284c-4e22-8e32-6f29ea237930', NULL, '0d8805a3-1d31-4a01-bf39-0807657aa848', 1788037200000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788082523506, 1788082523506, NULL, 1),
('f9778575-98d2-4ea1-965d-b8e025e02cfa', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 311100, 'TRANSFER', '63a53de8-284c-4e22-8e32-6f29ea237930', '86f251b7-2294-4d28-b169-debcf9ed0b77', NULL, 1790610540000, 'Visa Amanata → Amanata', NULL, NULL, NULL, NULL, NULL, NULL, 1790847101797, 1790847103047, NULL, 1),
('f97c9de0-106b-45a0-9d79-2c7337adccc0', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 50000, 'EXPENSE', '97d3f709-8069-4963-8c32-874009967823', NULL, '0594a349-03e2-4886-b6da-e6c0dc574d15', 1790920875736, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1790920886227, 1790946050150, NULL, 1),
('f9b924c0-538d-4457-bab1-92f9f10a6526', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1000000, 'EXPENSE', '44c112d3-4787-4ad6-b9c5-93649aaff584', NULL, 'a0daf981-8445-405e-b8a1-69729b8e67c2', 1788476400000, 'Prêt accordé', NULL, NULL, NULL, NULL, NULL, NULL, 1788558335066, 1788558335961, NULL, 1),
('fab6566c-caef-4e3c-934b-7678ba7eb6c8', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 10000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1789588680000, 'Tenti', NULL, NULL, NULL, NULL, NULL, NULL, 1789725547827, 1789725549255, NULL, 1),
('faf127be-459f-4bcb-a4fb-ffa7033cb9f9', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787504400000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787560758237, 1787560758237, NULL, 1),
('fbb542e7-cf21-40b3-9657-b726fd76d534', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1788595500000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788646589139, 1788646599275, NULL, 1),
('fbd4df86-a10c-4735-aa7a-7589bcd9b4fc', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 25000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1786904312343, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786904322506, 1786904322506, NULL, 1),
('fc15c02c-9d56-4ad5-ac28-65dc7f604896', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 200000, 'TRANSFER', '44c112d3-4787-4ad6-b9c5-93649aaff584', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1790685000000, 'MyNita → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1790803517762, 1790803518596, NULL, 1),
('fc4f4c3d-80b9-4fc3-9750-8262e8b33e34', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 20000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1787860800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787906703243, 1787909379274, NULL, 3),
('fca035ff-63ff-4ffc-b9c2-cda363c005e3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1789545600000, 'Petit Déjeuner', NULL, NULL, NULL, NULL, NULL, NULL, 1789725344022, 1789725360850, NULL, 2),
('fcac7914-12f4-4266-89ec-35b230cc2323', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 50000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '425749bf-cc1e-4de1-bf48-961529658997', 1787229000000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1787241675964, 1787241675964, NULL, 1),
('fcf20962-b7fc-4e96-96dd-be0e4e6b9ac3', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1500000, 'TRANSFER', 'aafbfdfb-47ec-4924-91dd-b7698277468a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 1789392120000, 'Wave → Espèces', NULL, NULL, NULL, NULL, NULL, NULL, 1789478576661, 1789479113817, NULL, 1),
('fd53f87e-540f-4566-ab68-a8bca11964a5', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 9877500, 'INCOME', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '3d512d37-6425-43a4-81ac-81ede7434f48', 1767299760000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786739865939, 1786739865939, NULL, 1),
('fe12a880-51d9-4eff-bdd6-5ab414f3cb75', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 90000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '45f98dc0-89d4-41a8-a6d0-15d5473d07dc', 1786800720000, 'Ninotech et Boubé', NULL, NULL, NULL, NULL, NULL, NULL, 1786822363448, 1786822363448, NULL, 1),
('ff67089b-dfc6-4150-a2ac-297a1b3ee681', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 1500000, 'EXPENSE', 'aafbfdfb-47ec-4924-91dd-b7698277468a', NULL, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', 1788039360000, '', NULL, NULL, NULL, '37c7d925-db3f-4290-b300-114f87c7444f', NULL, NULL, 1788082740329, 1788082740329, NULL, 1),
('ff7e55f8-070e-4457-a8cb-645247c38d41', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 5990000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, '999243ea-2013-4d43-8863-f876c3dbfd2e', 1785615660000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1786738911884, 1786738911884, NULL, 1),
('fffd7c7e-ff68-4909-878b-37c2ceba050b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 30000, 'EXPENSE', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', NULL, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 1788508800000, '', NULL, NULL, NULL, NULL, NULL, NULL, 1788528187680, 1788528188789, NULL, 1);

-- --------------------------------------------------------

--
-- Structure de la table `transaction_templates`
--

CREATE TABLE `transaction_templates` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(191) NOT NULL,
  `type` varchar(16) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `category_id` char(36) NOT NULL,
  `account_id` char(36) NOT NULL,
  `description` text NOT NULL,
  `is_favorite` tinyint(1) NOT NULL DEFAULT 0,
  `default_hour` tinyint(4) DEFAULT NULL,
  `default_minute` tinyint(4) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1,
  `source_transaction_id` char(36) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `transaction_templates`
--

INSERT INTO `transaction_templates` (`id`, `user_id`, `name`, `type`, `amount`, `category_id`, `account_id`, `description`, `is_favorite`, `default_hour`, `default_minute`, `created_at`, `updated_at`, `deleted_at`, `version`, `source_transaction_id`) VALUES
('2e7bb7ab-984c-4015-b5f2-8238f401facb', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Déjeuner', 'EXPENSE', 25000, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'Déjeuner', 1, 14, 0, 1789722866832, 1789722867209, NULL, 1, NULL),
('51e3db9b-c67c-4360-8623-ab2a8fa1e67b', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Claoud Pro', 'EXPENSE', 1167200, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', '63a53de8-284c-4e22-8e32-6f29ea237930', 'Claoud Pro', 0, NULL, NULL, 1790547673601, 1790847356616, 1790847356616, 2, '47852203-78a7-4eee-a788-a9d026e40b89'),
('75dc964f-37d0-4ef8-96eb-7b8e9ac8a3cf', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Dîner', 'EXPENSE', 25000, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'Dîner', 1, 20, 0, 1789722967163, 1789722967556, NULL, 1, NULL),
('995ee7de-67ef-4ecf-b96f-5ded7c57f3c7', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Salaire du mois', 'INCOME', 13870000, '8ff6b4d1-2775-48d0-93a7-dda7b93b5536', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'Qwiper ', 0, NULL, NULL, 1790241159080, 1790242785536, NULL, 1, NULL),
('c57ee9e4-ea0b-4737-bbf7-383cfdefe060', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Claoud Pro', 'EXPENSE', 1190400, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', '63a53de8-284c-4e22-8e32-6f29ea237930', 'Claoud Pro', 0, NULL, NULL, 1790847335184, 1790847336371, NULL, 1, 'e75a2435-e525-4f39-9d94-e0e586edb1c2'),
('dae48b40-98e2-4af4-9547-b4237906372f', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Carburant', 'EXPENSE', 100000, '2c1f02b9-05c3-4ebe-99ed-54c139279e4a', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'Carburant ', 0, NULL, NULL, 1789762008703, 1789763228704, NULL, 1, NULL),
('eb62ab85-39f7-4541-9ac2-1eed1783819c', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Petit Déjeuner', 'EXPENSE', 30000, 'd82d212d-c355-4c24-8c8d-2313ab03fe0b', 'ce3ba859-001c-41d6-b65e-32c016eeb52e', 'Petit Déjeuner', 1, 9, 0, 1789721142030, 1789725328730, NULL, 3, NULL),
('f0e2f1ee-3852-4872-9552-d942e9521b11', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'ChatGPT Go', 'EXPENSE', 282300, '420bfc42-9e1a-494a-ba85-10b52cd3b8dd', '63a53de8-284c-4e22-8e32-6f29ea237930', 'ChatGPT Go', 0, NULL, NULL, 1790513836969, 1790544935986, NULL, 1, 'd9c9aae6-eede-4085-8d20-54b6e9859396');

-- --------------------------------------------------------

--
-- Structure de la table `users`
--

CREATE TABLE `users` (
  `id` char(36) NOT NULL,
  `full_name` varchar(191) NOT NULL,
  `username` varchar(191) NOT NULL,
  `email` varchar(191) NOT NULL,
  `phone_number` varchar(32) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `security_question` varchar(64) NOT NULL,
  `security_answer_hash` varchar(255) NOT NULL,
  `photo_path` varchar(255) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `users`
--

INSERT INTO `users` (`id`, `full_name`, `username`, `email`, `phone_number`, `password_hash`, `security_question`, `security_answer_hash`, `photo_path`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('544458f0-b7c9-48f6-9559-c189c6fcc3f5', 'testing', 'testing', 'test@test.com', NULL, '$2y$10$kedgrUCHvWa5wLaqsMjDTejOodfXYjJ6LNNDAp5L3G/8IZgjewDSq', 'FIRST_PET_NAME', '$2y$10$xm4GdIAA4lW4evLzDL5nyOtFNqXFE9y0JEFiwoKyaXQ9TXOPjyysC', NULL, 1790343018385, 1790343018385, NULL, 1),
('548eba33-9bdf-4f1f-be06-60e2220b07c2', 'John Doe', 'johndoe123', 'johndoe@example.com', NULL, '$2y$10$fiOU5gioKjml77LXJXPTO.VV/Ot9.Xa0OgU6Xh5wWI3JV5fgFlz0i', 'FIRST_PET_NAME', '$2y$10$KCxXRQSdISJ/U4QK1.ce6OI3n/Y4sFA7oKqWN0jNxG9Rq/MJzxNfC', NULL, 1789922738007, 1789922738007, NULL, 1),
('5e1c8db5-c66b-48bc-8b53-d43a1fc8e701', 'jjjj', 'nnnnj', '123456@gmail.com', '999999999', '$2y$10$ArlcoKUYgk0vSIO7HbsSYONTCHCGNZhERaT96Jq0nlo1.qPLqKvaG', 'FIRST_PET_NAME', '$2y$10$jnuY8En6IKUKlXChGrkLdOgdZA.rX5jQOWmquFYYMoU5z0vvSqZJG', NULL, 1790346614013, 1790346614013, NULL, 1),
('7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'Bachir', 'Abdoul Kader', 'bachirabdoulkader62331@gmail.com', '94961793', '$2b$10$KRQQL/W1SjryKTsft6LGPOjrmsYOeQStrqeS9NL2T2kNYlgB7LcdC', 'MOTHER_MAIDEN_NAME', '$2b$10$BMvC2VrQC.k.BO3MTbB1x.82Wk1itDv47qgn1/z/NdgEafH/Nr5im', 'avatars/7adaeab9-a066-11f1-9a6f-9d51bec7aafd/6.jpg', 1787649743344, 1790199668515, NULL, 6),
('87704bcb-2b48-4f9a-a2ce-128b2cba698e', 'Test User', 'testuser123', 'testuser123@example.com', '96123456', '$2y$10$d0QRTlvcOlJZhwEO1GsXHuTuAqL5gdMpTbmRi01Yz/l5DmmCoc2Pu', 'FIRST_PET_NAME', '$2y$10$h9iCzPzBlj3VDpajPYZKsubu0y2oa9oVHYzYu/8j000W4JoG/pHcO', NULL, 1789916128616, 1789916128616, NULL, 1),
('911d1263-1149-4d4c-8570-1b6644a6d9a5', 'Ali Moussa', 'Ali', 'ali@gmail.com', '96000000', '$2y$10$vA7ID1EuGOYAUARiKyZkEeAPbb8IHUd6IQsAl8gL8XHpf04CNDPhy', 'BIRTH_CITY', '$2y$10$VXecra2K7X..YLZvcx2xu.vM4lvc5oRI.bKXv5k5qvfp4uf9wEV1a', NULL, 1788971271334, 1788971271334, NULL, 1),
('ca06ac92-8ed0-4e21-b5d9-9693445a5a7e', 'pelopidas ko', 'alkisax', 'alkisax@gmail.com', NULL, '$2y$10$qI33PQAunyq5H5EGX1i1ou.fN60ZgbHDE3wX/YRV38DEwcUJIhJ7S', 'FIRST_PET_NAME', '$2y$10$Sws1YsINIr6ThblkHMac.O8eqUa70hFXfYkLTyxA4yPdO0oiQ5Y5G', NULL, 1790279278673, 1790279278673, NULL, 1),
('ca0d564e-c72c-478a-8933-ad1797fcfab2', 'Test', 'Test', 'test@gmail.com', '11111111', '$2y$10$LbsLa.7nMO0fIhQv18AY6esUSMJqrJVQL55TIBYaC8aTpHGuk/JC6', 'FIRST_PET_NAME', '$2y$10$i094Jl/C7QINmFBiydLTzOJ5.uCzBk8RZEZ0opTDjF/xoTOW4Q9Pm', NULL, 1789916308625, 1789916308625, NULL, 1);

-- --------------------------------------------------------

--
-- Structure de la table `user_devices`
--

CREATE TABLE `user_devices` (
  `id` bigint(20) NOT NULL,
  `user_id` char(36) NOT NULL,
  `installation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `fcm_token` varchar(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `platform` varchar(16) NOT NULL,
  `app_version` varchar(32) DEFAULT NULL,
  `locale` varchar(16) DEFAULT NULL,
  `auth_token_id` bigint(20) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `last_seen_at` bigint(20) NOT NULL,
  `revoked_at` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `user_preferences`
--

CREATE TABLE `user_preferences` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `theme_mode` varchar(16) NOT NULL,
  `currency_code` char(3) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `deleted_at` bigint(20) DEFAULT NULL,
  `version` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `user_preferences`
--

INSERT INTO `user_preferences` (`id`, `user_id`, `theme_mode`, `currency_code`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
('202ba333-72bd-4e4d-a1bf-d0a33ab68c93', '544458f0-b7c9-48f6-9559-c189c6fcc3f5', 'SYSTEM', 'XOF', 1790503985448, 1790503989313, NULL, 2),
('6f44e3e9-bd01-4d79-9c25-3d86aa0ec3a6', '7adaeab9-a066-11f1-9a6f-9d51bec7aafd', 'DARK', 'XOF', 1787781788861, 1789038465768, NULL, 14),
('bb98b23a-f8c4-41cf-9007-4b3c248814e7', 'ca0d564e-c72c-478a-8933-ad1797fcfab2', 'DARK', 'NGN', 1790352451846, 1791214490150, NULL, 31);

--
-- Index pour les tables déchargées
--

--
-- Index pour la table `accounts`
--
ALTER TABLE `accounts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_accounts_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `auth_tokens`
--
ALTER TABLE `auth_tokens`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_auth_tokens_token_hash` (`token_hash`),
  ADD KEY `idx_auth_tokens_user` (`user_id`);

--
-- Index pour la table `budgets`
--
ALTER TABLE `budgets`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_budgets_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_budgets_category` (`category_id`);

--
-- Index pour la table `categories`
--
ALTER TABLE `categories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_categories_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `financial_plans`
--
ALTER TABLE `financial_plans`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_financial_plans_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `financial_plan_items`
--
ALTER TABLE `financial_plan_items`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_plan_items_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_plan_items_plan` (`plan_id`),
  ADD KEY `idx_plan_items_category` (`category_id`);

--
-- Index pour la table `loans`
--
ALTER TABLE `loans`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_loans_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_loans_person` (`person_id`),
  ADD KEY `idx_loans_account` (`account_id`),
  ADD KEY `idx_loans_gift_transaction` (`gift_transaction_id`);

--
-- Index pour la table `loan_payments`
--
ALTER TABLE `loan_payments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_loan_payments_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_loan_payments_loan` (`loan_id`),
  ADD KEY `idx_loan_payments_account` (`account_id`);

--
-- Index pour la table `persons`
--
ALTER TABLE `persons`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_persons_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `receipts`
--
ALTER TABLE `receipts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_receipts_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `recurring_transactions`
--
ALTER TABLE `recurring_transactions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_recurring_transactions_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_recurring_transactions_account` (`account_id`),
  ADD KEY `idx_recurring_transactions_category` (`category_id`);

--
-- Index pour la table `recurring_transaction_occurrences`
--
ALTER TABLE `recurring_transaction_occurrences`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_occurrences_rule_date` (`recurring_transaction_id`,`scheduled_date`),
  ADD KEY `idx_occurrences_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `savings_goals`
--
ALTER TABLE `savings_goals`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_savings_goals_user_updated` (`user_id`,`updated_at`);

--
-- Index pour la table `sync_conflicts`
--
ALTER TABLE `sync_conflicts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_sync_conflicts_user` (`user_id`),
  ADD KEY `idx_sync_conflicts_entity` (`entity_type`,`entity_id`);

--
-- Index pour la table `transactions`
--
ALTER TABLE `transactions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_transactions_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_transactions_account` (`account_id`),
  ADD KEY `idx_transactions_category` (`category_id`),
  ADD KEY `fk_transactions_transfer_account` (`transfer_account_id`);

--
-- Index pour la table `transaction_templates`
--
ALTER TABLE `transaction_templates`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_transaction_templates_user_updated` (`user_id`,`updated_at`),
  ADD KEY `idx_transaction_templates_account` (`account_id`),
  ADD KEY `idx_transaction_templates_category` (`category_id`),
  ADD KEY `idx_transaction_templates_source_transaction` (`source_transaction_id`);

--
-- Index pour la table `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_users_username` (`username`),
  ADD UNIQUE KEY `uq_users_email` (`email`);

--
-- Index pour la table `user_devices`
--
ALTER TABLE `user_devices`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_user_devices_installation` (`installation_id`),
  ADD UNIQUE KEY `uq_user_devices_fcm_token` (`fcm_token`),
  ADD KEY `idx_user_devices_user_active` (`user_id`,`revoked_at`),
  ADD KEY `idx_user_devices_auth_token` (`auth_token_id`);

--
-- Index pour la table `user_preferences`
--
ALTER TABLE `user_preferences`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_user_preferences_user` (`user_id`);

--
-- AUTO_INCREMENT pour les tables déchargées
--

--
-- AUTO_INCREMENT pour la table `auth_tokens`
--
ALTER TABLE `auth_tokens`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=402;

--
-- AUTO_INCREMENT pour la table `sync_conflicts`
--
ALTER TABLE `sync_conflicts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=45;

--
-- AUTO_INCREMENT pour la table `user_devices`
--
ALTER TABLE `user_devices`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- Contraintes pour les tables déchargées
--

--
-- Contraintes pour la table `accounts`
--
ALTER TABLE `accounts`
  ADD CONSTRAINT `fk_accounts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `auth_tokens`
--
ALTER TABLE `auth_tokens`
  ADD CONSTRAINT `fk_auth_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `budgets`
--
ALTER TABLE `budgets`
  ADD CONSTRAINT `fk_budgets_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `categories`
--
ALTER TABLE `categories`
  ADD CONSTRAINT `fk_categories_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `financial_plans`
--
ALTER TABLE `financial_plans`
  ADD CONSTRAINT `fk_financial_plans_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `financial_plan_items`
--
ALTER TABLE `financial_plan_items`
  ADD CONSTRAINT `fk_plan_items_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `loans`
--
ALTER TABLE `loans`
  ADD CONSTRAINT `fk_loans_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `loan_payments`
--
ALTER TABLE `loan_payments`
  ADD CONSTRAINT `fk_loan_payments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `persons`
--
ALTER TABLE `persons`
  ADD CONSTRAINT `fk_persons_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `receipts`
--
ALTER TABLE `receipts`
  ADD CONSTRAINT `fk_receipts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `recurring_transactions`
--
ALTER TABLE `recurring_transactions`
  ADD CONSTRAINT `fk_recurring_transactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `recurring_transaction_occurrences`
--
ALTER TABLE `recurring_transaction_occurrences`
  ADD CONSTRAINT `fk_occurrences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `savings_goals`
--
ALTER TABLE `savings_goals`
  ADD CONSTRAINT `fk_savings_goals_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `sync_conflicts`
--
ALTER TABLE `sync_conflicts`
  ADD CONSTRAINT `fk_sync_conflicts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `transactions`
--
ALTER TABLE `transactions`
  ADD CONSTRAINT `fk_transactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `transaction_templates`
--
ALTER TABLE `transaction_templates`
  ADD CONSTRAINT `fk_transaction_templates_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `user_devices`
--
ALTER TABLE `user_devices`
  ADD CONSTRAINT `fk_user_devices_auth_token` FOREIGN KEY (`auth_token_id`) REFERENCES `auth_tokens` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_user_devices_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `user_preferences`
--
ALTER TABLE `user_preferences`
  ADD CONSTRAINT `fk_user_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
