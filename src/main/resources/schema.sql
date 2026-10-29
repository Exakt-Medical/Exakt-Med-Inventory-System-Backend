-- =============================================================================
-- Exakt Medical Inventory System - PostgreSQL Database Schema
-- Matches JPA/Hibernate (SpringPhysicalNamingStrategy) entity mappings
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ORGANIZATION & ACCESS CONTROL
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
    contact_person VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255),
    status VARCHAR(255) NOT NULL CHECK (status IN ('Active', 'Inactive')),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_facilities_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- Entity: User
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL CHECK (role IN ('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')),
    status BOOLEAN NOT NULL,
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

-- -----------------------------------------------------------------------------
-- 2. REFERENCE LIBRARIES
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

-- Entity: LibLocation
CREATE TABLE IF NOT EXISTS lib_locations (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT,
    code VARCHAR(50) NOT NULL,
    storage_type VARCHAR(30) NOT NULL DEFAULT 'ROOM_TEMPERATURE'
        CHECK (storage_type IN ('ROOM_TEMPERATURE', 'COLD_STORAGE', 'FREEZER', 'HAZARDOUS', 'OTHER')),
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_lib_locations_facility FOREIGN KEY (facility_id) REFERENCES facilities(id)
);

CREATE INDEX IF NOT EXISTS idx_lib_loc_facility ON lib_locations (facility_id);
CREATE INDEX IF NOT EXISTS idx_lib_loc_code ON lib_locations (code);

-- -----------------------------------------------------------------------------
-- 3. INVENTORY & CATALOG
-- -----------------------------------------------------------------------------

-- Entity: Sku
CREATE TABLE IF NOT EXISTS sku (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT,
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
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_facility_sku_name UNIQUE (facility_id, name),
    CONSTRAINT fk_sku_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_sku_medicine FOREIGN KEY (medicine_id) REFERENCES lib_medicine(id)
);

CREATE INDEX IF NOT EXISTS idx_sku_facility_brand ON sku (facility_id, brand_name);
CREATE INDEX IF NOT EXISTS idx_sku_facility_units ON sku (facility_id, units);
CREATE INDEX IF NOT EXISTS idx_sku_facility_name ON sku (facility_id, name);

-- -----------------------------------------------------------------------------
-- 4. PURCHASING, REPLENISHMENT & BATCH RECEIVING
-- -----------------------------------------------------------------------------

-- Entity: Supplier
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    address VARCHAR(255),
    payment_terms VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Inactive')),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_supplier_facility_email UNIQUE (facility_id, email),
    CONSTRAINT fk_suppliers_facility FOREIGN KEY (facility_id) REFERENCES facilities(id)
);

-- Entity: Order
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT,
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
    CONSTRAINT fk_orders_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE INDEX IF NOT EXISTS idx_orders_facility_created ON orders (facility_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_facility_status ON orders (facility_id, status);
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

-- Entity: RestockRequest
CREATE TABLE IF NOT EXISTS restock_requests (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    requested_units BIGINT NOT NULL,
    reason VARCHAR(255),
    order_id BIGINT,
    ordered_item_id BIGINT,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_restock_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_restock_sku FOREIGN KEY (sku_id) REFERENCES sku(id),
    CONSTRAINT fk_restock_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_restock_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_restock_ordered_item FOREIGN KEY (ordered_item_id) REFERENCES ordered_items(id)
);

-- Entity: Batch
CREATE TABLE IF NOT EXISTS batches (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL,
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
    CONSTRAINT fk_batches_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_batches_ordered_item FOREIGN KEY (ordered_item_id) REFERENCES ordered_items(id),
    CONSTRAINT fk_batches_location FOREIGN KEY (location_id) REFERENCES lib_locations(id)
);

CREATE INDEX IF NOT EXISTS idx_batch_facility_received ON batches (facility_id, received_at);
CREATE INDEX IF NOT EXISTS idx_batch_facility_num ON batches (facility_id, batch_num);
CREATE INDEX IF NOT EXISTS idx_batch_facility_status ON batches (facility_id, status);
CREATE INDEX IF NOT EXISTS idx_batch_location ON batches (location_id);

-- -----------------------------------------------------------------------------
-- 5. DISPENSING, LEDGER & AUDIT LOGS
-- -----------------------------------------------------------------------------

-- Entity: DispensedLog
CREATE TABLE IF NOT EXISTS dispensed_logs (
    id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT,
    patient_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255) NOT NULL,
    units_dispensed BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL CHECK (status IN ('Pending', 'Received')),
    dispensed_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6),
    CONSTRAINT fk_dispensed_logs_sku FOREIGN KEY (sku_id) REFERENCES sku(id)
);

-- Entity: InventoryTransaction
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL,
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
    CONSTRAINT fk_inv_tx_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_inv_tx_sku FOREIGN KEY (sku_id) REFERENCES sku(id),
    CONSTRAINT fk_inv_tx_batch FOREIGN KEY (batch_id) REFERENCES batches(id),
    CONSTRAINT fk_inv_tx_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_inv_tx_facility_sku ON inventory_transactions (facility_id, sku_id, created_at);
CREATE INDEX IF NOT EXISTS idx_inv_tx_batch ON inventory_transactions (batch_id);
CREATE INDEX IF NOT EXISTS idx_inv_tx_created_at ON inventory_transactions (created_at);

-- Entity: AuditLog
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL,
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
    CONSTRAINT fk_audit_logs_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_facility_created ON audit_logs (facility_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_module_severity ON audit_logs (module, severity);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs (user_id);

