-- =============================================================================
-- Exakt Medical Inventory System - PostgreSQL Database Schema
-- Matches JPA/Hibernate (SpringPhysicalNamingStrategy) entity mappings
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ORGANIZATION, FACILITIES & PHARMACIES
-- -----------------------------------------------------------------------------

-- Entity: Project
CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    ipkras_project_id BIGINT UNIQUE,
    name VARCHAR(255) NOT NULL,
    project_code VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

-- Entity: Facility
CREATE TABLE IF NOT EXISTS facilities (
    id BIGSERIAL PRIMARY KEY,
    ipkras_facility_id BIGINT UNIQUE,
    project_id BIGINT NOT NULL,
    facility_code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    status VARCHAR(255) NOT NULL CHECK (status IN ('Active', 'Inactive')),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_facilities_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- Entity: Pharmacy
CREATE TABLE IF NOT EXISTS pharmacies (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_code VARCHAR(255) NOT NULL UNIQUE,
    pharmacy_name VARCHAR(255) NOT NULL,
    facility_id BIGINT NOT NULL,
    lgu_id BIGINT,
    address VARCHAR(255),
    license_number VARCHAR(255),
    status VARCHAR(255) NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Inactive')),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_pharmacies_facility FOREIGN KEY (facility_id) REFERENCES facilities(id)
);

CREATE INDEX IF NOT EXISTS idx_pharmacies_facility ON pharmacies (facility_id);

-- -----------------------------------------------------------------------------
-- 2. USERS & ACCESS CONTROL LINKS
-- -----------------------------------------------------------------------------

-- Entity: User
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL CHECK (role IN ('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')),
    status BOOLEAN NOT NULL DEFAULT TRUE,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

-- Entity: UserFacilityLink
CREATE TABLE IF NOT EXISTS user_facility_links (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    assigned_at TIMESTAMP(6),
    CONSTRAINT uk_user_facility_link UNIQUE (user_id, facility_id),
    CONSTRAINT fk_ufl_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ufl_facility FOREIGN KEY (facility_id) REFERENCES facilities(id)
);

-- Entity: UserPharmacyLink
CREATE TABLE IF NOT EXISTS user_pharmacy_links (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    assigned_at TIMESTAMP(6),
    CONSTRAINT uk_user_pharmacy_link UNIQUE (user_id, pharmacy_id),
    CONSTRAINT fk_upl_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_upl_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id)
);

-- -----------------------------------------------------------------------------
-- 3. REFERENCE LIBRARIES
-- -----------------------------------------------------------------------------

-- Entity: LibMedicine
CREATE TABLE IF NOT EXISTS lib_medicine (
    id BIGSERIAL PRIMARY KEY,
    drug_code VARCHAR(455) NOT NULL,
    drug_description VARCHAR(455) NOT NULL,
    gen_code VARCHAR(45),
    salt_code VARCHAR(45),
    form_code VARCHAR(45),
    strength_code VARCHAR(45),
    unit_code VARCHAR(45),
    package_code VARCHAR(45)
);

-- Entity: LibPackagingUnit
CREATE TABLE IF NOT EXISTS lib_packaging_unit (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    default_units INTEGER
);

-- Entity: LibLocation (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS lib_locations (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    storage_type VARCHAR(30) NOT NULL DEFAULT 'ROOM_TEMPERATURE'
        CHECK (storage_type IN ('ROOM_TEMPERATURE', 'COLD_STORAGE', 'FREEZER', 'HAZARDOUS', 'OTHER')),
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_lib_locations_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id)
);

CREATE INDEX IF NOT EXISTS idx_lib_loc_pharmacy ON lib_locations (pharmacy_id);
CREATE INDEX IF NOT EXISTS idx_lib_loc_code ON lib_locations (code);

-- -----------------------------------------------------------------------------
-- 4. INVENTORY & SKU CATALOG
-- -----------------------------------------------------------------------------

-- Entity: Sku (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS sku (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    medicine_id BIGINT,
    name VARCHAR(255) NOT NULL,
    brand_name VARCHAR(255) NOT NULL,
    dosage_form VARCHAR(255) NOT NULL,
    dosage_strength VARCHAR(255),
    packaging_unit VARCHAR(255) NOT NULL,
    units BIGINT NOT NULL DEFAULT 0,
    minimum_level BIGINT NOT NULL,
    reorder_level BIGINT NOT NULL,
    maximum_level BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'Active',
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_pharmacy_sku_name UNIQUE (pharmacy_id, name),
    CONSTRAINT fk_sku_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_sku_medicine FOREIGN KEY (medicine_id) REFERENCES lib_medicine(id)
);

CREATE INDEX IF NOT EXISTS idx_sku_pharmacy_brand ON sku (pharmacy_id, brand_name);
CREATE INDEX IF NOT EXISTS idx_sku_pharmacy_units ON sku (pharmacy_id, units);
CREATE INDEX IF NOT EXISTS idx_sku_pharmacy_name ON sku (pharmacy_id, name);
CREATE INDEX IF NOT EXISTS idx_sku_pharmacy_status ON sku (pharmacy_id, status);

-- Safe migration for existing schemas
ALTER TABLE sku ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'Active';

-- -----------------------------------------------------------------------------
-- 5. PURCHASING, REPLENISHMENT & BATCH RECEIVING
-- -----------------------------------------------------------------------------

-- Entity: Supplier (Facility and optional Pharmacy scoping)
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    pharmacy_id BIGINT,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    address VARCHAR(255),
    payment_terms VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Inactive')),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_supplier_facility_pharmacy_email UNIQUE (facility_id, pharmacy_id, email),
    CONSTRAINT fk_suppliers_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_suppliers_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id)
);

-- Entity: Order (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    supplier_id BIGINT,
    purchase_order_num VARCHAR(255) NOT NULL,
    priority VARCHAR(255) NOT NULL,
    total_ordered_units BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    notes VARCHAR(255),
    status VARCHAR(255) NOT NULL DEFAULT 'Pending'
        CHECK (status IN ('Pending', 'Approved', 'Denied', 'Partially_Received', 'Received')),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_orders_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE INDEX IF NOT EXISTS idx_orders_pharmacy_created ON orders (pharmacy_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_pharmacy_status ON orders (pharmacy_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_po_num ON orders (purchase_order_num);

-- Entity: OrderedItem
CREATE TABLE IF NOT EXISTS ordered_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT,
    sku_id BIGINT,
    ordered_units BIGINT NOT NULL,
    received_units BIGINT DEFAULT 0,
    price BIGINT NOT NULL,
    price_per_unit DOUBLE PRECISION,
    CONSTRAINT fk_ordered_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_ordered_items_sku FOREIGN KEY (sku_id) REFERENCES sku(id)
);

-- Entity: RestockRequest (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS restock_requests (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    requested_units BIGINT NOT NULL,
    reason VARCHAR(255),
    order_id BIGINT,
    ordered_item_id BIGINT,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_restock_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_restock_sku FOREIGN KEY (sku_id) REFERENCES sku(id),
    CONSTRAINT fk_restock_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_restock_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_restock_ordered_item FOREIGN KEY (ordered_item_id) REFERENCES ordered_items(id)
);

-- Entity: Batch (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS batches (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    ordered_item_id BIGINT,
    location_id BIGINT,
    batch_num VARCHAR(255) NOT NULL,
    units BIGINT NOT NULL DEFAULT 0,
    manufacture_date DATE,
    expiry_date DATE NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'Available'
        CHECK (status IN ('Quarantined', 'Available', 'Expired')),
    notes VARCHAR(255),
    sku_deducted BOOLEAN DEFAULT FALSE,
    received_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_batches_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_batches_ordered_item FOREIGN KEY (ordered_item_id) REFERENCES ordered_items(id),
    CONSTRAINT fk_batches_location FOREIGN KEY (location_id) REFERENCES lib_locations(id)
);

CREATE INDEX IF NOT EXISTS idx_batch_pharmacy_received ON batches (pharmacy_id, received_at);
CREATE INDEX IF NOT EXISTS idx_batch_pharmacy_num ON batches (pharmacy_id, batch_num);
CREATE INDEX IF NOT EXISTS idx_batch_pharmacy_status ON batches (pharmacy_id, status);
CREATE INDEX IF NOT EXISTS idx_batch_location ON batches (location_id);

-- -----------------------------------------------------------------------------
-- 6. DISPENSING, LEDGER & AUDIT LOGS
-- -----------------------------------------------------------------------------

-- Entity: Patient (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS patients (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_patients_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy(id)
);

CREATE INDEX IF NOT EXISTS idx_patients_pharmacy_name ON patients (pharmacy_id, name);

-- Entity: DispensedLog
CREATE TABLE IF NOT EXISTS dispensed_logs (
    id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT,
    patient_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255) NOT NULL,
    units_dispensed BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL CHECK (status IN ('Pending', 'Received')),
    dispensed_by VARCHAR(255) NOT NULL,
    dispensed_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6),
    CONSTRAINT fk_dispensed_logs_sku FOREIGN KEY (sku_id) REFERENCES sku(id)
);

-- Entity: InventoryTransaction (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    batch_id BIGINT,
    user_id BIGINT,
    transaction_type VARCHAR(40) NOT NULL
        CHECK (transaction_type IN (
            'RECEIPT',
            'QUARANTINE_RELEASE',
            'ADJUSTMENT_ADD',
            'ADJUSTMENT_SUB',
            'DISPENSE',
            'EXPIRY_DEDUCTION',
            'INITIAL_STOCK'
        )),
    reference_number VARCHAR(100),
    batch_num VARCHAR(100),
    expiry_date DATE,
    quantity_in BIGINT,
    quantity_out BIGINT,
    previous_balance BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    notes VARCHAR(1000),
    authorized_by VARCHAR(150),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_inv_tx_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_inv_tx_sku FOREIGN KEY (sku_id) REFERENCES sku(id),
    CONSTRAINT fk_inv_tx_batch FOREIGN KEY (batch_id) REFERENCES batches(id),
    CONSTRAINT fk_inv_tx_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_inv_tx_pharmacy_sku ON inventory_transactions (pharmacy_id, sku_id, created_at);
CREATE INDEX IF NOT EXISTS idx_inv_tx_batch ON inventory_transactions (batch_id);
CREATE INDEX IF NOT EXISTS idx_inv_tx_created_at ON inventory_transactions (created_at);

-- Entity: AuditLog (Scoped to Pharmacy)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id BIGINT NOT NULL,
    user_id BIGINT,
    user_name VARCHAR(100),
    user_role VARCHAR(50),
    module VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    action_label VARCHAR(150) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO'
        CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL')),
    target VARCHAR(100),
    target_id BIGINT,
    description TEXT NOT NULL,
    visible_roles VARCHAR(100) NOT NULL DEFAULT 'Admin,Pharmacist,Procurement',
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_audit_logs_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_pharmacy_created ON audit_logs (pharmacy_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_module_severity ON audit_logs (module, severity);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs (user_id);

-- -----------------------------------------------------------------------------
-- 7. REFRESH IDENTITY / SERIAL SEQUENCES FOR ALL ENTITY TABLES
-- Ensures auto-increment sequences stay in sync with MAX(id) after manual
-- inserts, data imports, or seed scripts.
-- -----------------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('projects', 'id'), COALESCE((SELECT MAX(id) FROM projects), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('facilities', 'id'), COALESCE((SELECT MAX(id) FROM facilities), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('pharmacies', 'id'), COALESCE((SELECT MAX(id) FROM pharmacies), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('user_facility_links', 'id'), COALESCE((SELECT MAX(id) FROM user_facility_links), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('user_pharmacy_links', 'id'), COALESCE((SELECT MAX(id) FROM user_pharmacy_links), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('lib_medicine', 'id'), COALESCE((SELECT MAX(id) FROM lib_medicine), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('lib_packaging_unit', 'id'), COALESCE((SELECT MAX(id) FROM lib_packaging_unit), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('lib_locations', 'id'), COALESCE((SELECT MAX(id) FROM lib_locations), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sku', 'id'), COALESCE((SELECT MAX(id) FROM sku), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('suppliers', 'id'), COALESCE((SELECT MAX(id) FROM suppliers), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('orders', 'id'), COALESCE((SELECT MAX(id) FROM orders), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('ordered_items', 'id'), COALESCE((SELECT MAX(id) FROM ordered_items), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('restock_requests', 'id'), COALESCE((SELECT MAX(id) FROM restock_requests), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('batches', 'id'), COALESCE((SELECT MAX(id) FROM batches), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('dispensed_logs', 'id'), COALESCE((SELECT MAX(id) FROM dispensed_logs), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('inventory_transactions', 'id'), COALESCE((SELECT MAX(id) FROM inventory_transactions), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('audit_logs', 'id'), COALESCE((SELECT MAX(id) FROM audit_logs), 0) + 1, false);
