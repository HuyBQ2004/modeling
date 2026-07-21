-- Database creation and connection is handled by Docker compose environment variable (POSTGRES_DB=rice_store)

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ROLE_ADMIN', 'ROLE_EMPLOYEE', 'ROLE_OWNER')),
    name VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    phone VARCHAR(20) NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE stores (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    address VARCHAR(255) NULL,
    phone VARCHAR(20) NULL,
    email VARCHAR(100) NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (created_by) REFERENCES users(username)
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    price DECIMAL(15,2) NOT NULL CHECK (price >= 0),   
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    store_id BIGINT NOT NULL,
    address VARCHAR(255),
    product_name VARCHAR(255),
    product_id BIGINT,
    quantity INT CHECK (quantity >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (store_id) REFERENCES stores(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255) NULL,
    email VARCHAR(255) NULL,
    debt_balance DECIMAL(15,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    total_price DECIMAL(15,2) NOT NULL CHECK (total_price >= 0),
    discount DECIMAL(15,2) DEFAULT 0 CHECK (discount >= 0),
    quantity INT CHECK (quantity >= 0),
    final_amount DECIMAL(15,2) NOT NULL CHECK (final_amount >= 0),
    payment_status VARCHAR(20) NOT NULL CHECK (payment_status IN ('Paid', 'Unpaid', 'In_debt')),
    note VARCHAR(255) NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('Purchase', 'Sale')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE invoice_details (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(15,2) NOT NULL CHECK (unit_price >= 0),
    total_price DECIMAL(15,2) NOT NULL CHECK (total_price >= 0),
    zone_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (zone_id) REFERENCES zones(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE debt_records (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('Customer_debt_shop', 'Customer_return_shop', 'Shop_debt_customer', 'Shop_return_customer')),
    amount DECIMAL(15,2) NOT NULL,
    note VARCHAR(255),
    create_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE shifts (
    id BIGSERIAL PRIMARY KEY,
    shift_code VARCHAR(50) NOT NULL UNIQUE,
    shift_name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    shift_type VARCHAR(20) CHECK (shift_type = 'PART_TIME'),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE work_shifts (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    shift_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    scheduled_start_time TIMESTAMP NOT NULL,
    scheduled_end_time TIMESTAMP NOT NULL,
    total_work_hours DECIMAL(5,2),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE forgotPassword (
    fpid BIGSERIAL PRIMARY KEY,
    otp INT NOT NULL,
    expiration_time TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL
);

INSERT INTO shifts (
    shift_code, 
    shift_name, 
    start_time, 
    end_time, 
    shift_type, 
    created_by, 
    updated_by)
VALUES 
    ('SHIFT001', 'Morning Shift', '08:00', '16:00', 'PART_TIME', 1, 1),
    ('SHIFT002', 'Afternoon Shift', '16:00', '00:00', 'PART_TIME', 2, 2),
    ('SHIFT003', 'Night Shift', '00:00', '08:00', 'PART_TIME', 3, 3);

ALTER TABLE zones DROP COLUMN product_name;

ALTER TABLE invoice_details 
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT,
    ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

CREATE TABLE customer_change_histories (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    changed_field VARCHAR(255) NOT NULL,
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    additional_info VARCHAR(255),
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_CustomerChangeHistory_Customer 
        FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT FK_CustomerChangeHistory_User 
        FOREIGN KEY (changed_by) REFERENCES users(id)
);

CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_store_createdat ON invoices(store_id, created_at);
CREATE INDEX idx_invoice_details_invoice ON invoice_details(invoice_id);
CREATE INDEX idx_invoice_details_product ON invoice_details(product_id);
CREATE INDEX idx_debtrecords_customer_createon ON debt_records(customer_id, create_on);

ANALYZE invoices;
ANALYZE invoice_details;
ANALYZE debt_records;
