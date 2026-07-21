CREATE DATABASE rice_store;
GO

USE rice_store;
GO

CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    username NVARCHAR(50) NOT NULL UNIQUE,
    password NVARCHAR(255) NOT NULL,
    email NVARCHAR(100) NOT NULL UNIQUE,
    role NVARCHAR(20) NOT NULL CHECK (role IN ('ROLE_ADMIN', 'ROLE_EMPLOYEE', 'ROLE_OWNER')),
    name NVARCHAR(100) NULL,
    address NVARCHAR(255) NULL,
    phone NVARCHAR(20) NULL,
    note NVARCHAR(255) NULL,
	created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    is_deleted BIT DEFAULT 0,
);
GO

CREATE TABLE stores (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(50) NOT NULL,
    address NVARCHAR(255) NULL,
    phone NVARCHAR(20) NULL,
	email NVARCHAR(100) NULL,
    note NVARCHAR(255) NULL,
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    created_by NVARCHAR(50) NOT NULL,
    updated_by NVARCHAR(50) NULL,
    is_deleted BIT DEFAULT 0,
    FOREIGN KEY (created_by) REFERENCES users(username)
);
GO




CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(255) NULL,
    price DECIMAL(15,2) NOT NULL CHECK (price >= 0),   
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    created_by BIGINT NOT NULL,
    updated_by NVARCHAR(50) NULL,
    is_deleted BIT DEFAULT 0,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
GO
CREATE TABLE zones (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
	store_id Bigint not null,
	address nvarchar(255),
	product_name nvarchar(255),
	product_id bigint ,
	quantity int check (quantity >=0),
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    created_by BIGINT NOT NULL,
    updated_by NVARCHAR(50) NULL,
    is_deleted BIT DEFAULT 0,
	FOREIGN KEY (store_id) REFERENCES stores(id),
	foreign key (product_id) references products(id)
);
GO
CREATE TABLE customers (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    phone NVARCHAR(20) NOT NULL,
    address NVARCHAR(255) NULL,
    email NVARCHAR(255) NULL,
    debt_balance DECIMAL(15,2) DEFAULT 0,
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    created_by BIGINT NOT NULL,
    updated_by NVARCHAR(50) NULL,
    is_deleted BIT DEFAULT 0,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
GO

CREATE TABLE invoices (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
	store_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    total_price DECIMAL(15,2) NOT NULL CHECK (total_price >= 0),
    discount DECIMAL(15,2) DEFAULT 0 CHECK (discount >= 0),
	quantity int check (quantity >=0),
	final_amount DECIMAL(15,2) NOT NULL CHECK (final_amount >= 0),
	payment_status NVARCHAR(20) NOT NULL CHECK (payment_status IN ('Paid', 'Unpaid', 'In_debt')),
	note NVARCHAR(255) NULL,
	type nvarchar(20) not null check (type in('Purchase' , 'Sale')),
	created_at DATETIME DEFAULT GETDATE(),
	updated_at DATETIME DEFAULT GETDATE(),
    created_by BIGINT NOT NULL,
	updated_by NVARCHAR(50) NULL,
    is_deleted BIT DEFAULT 0,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id),
);
GO

CREATE TABLE invoice_details (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(15,2) NOT NULL CHECK (unit_price >= 0),
	total_price DECIMAL(15,2) NOT NULL CHECK (total_price >= 0),
	zone_id bigint not null ,
	customer_id bigint not null,
    FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
	FOREIGN KEY (zone_id) REFERENCES zones(id),
	FOREIGN KEY (customer_id) REFERENCES customers(id),
);
GO


CREATE TABLE debt_records (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    type NVARCHAR(50) NOT NULL CHECK (type IN ('Customer_debt_shop' , 'Customer_return_shop' , 'Shop_debt_customer' ,'Shop_return_customer')),
    amount DECIMAL(15,2) NOT NULL,
	note nvarchar(255),
	create_on DateTime Default getdate(),
    created_at DATETIME DEFAULT GETDATE(),
	updated_at DATETIME DEFAULT GETDATE(),
    created_by BIGINT NOT NULL,
	updated_by NVARCHAR(50) NULL,
    is_deleted BIT DEFAULT 0,
    FOREIGN KEY (customer_id) REFERENCES customers(id),
	FOREIGN KEY (created_by) REFERENCES users(id)

);

CREATE TABLE shifts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    shift_code NVARCHAR(50) NOT NULL UNIQUE,
    shift_name NVARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    shift_type NVARCHAR(20) CHECK (shift_type = 'PART_TIME'),
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME NOT NULL DEFAULT GETDATE(),
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BIT DEFAULT 0
);

CREATE TABLE work_shifts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    shift_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    scheduled_start_time DATETIME NOT NULL,
    scheduled_end_time DATETIME NOT NULL,
    total_work_hours DECIMAL(5,2),
    notes TEXT,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME NOT NULL DEFAULT GETDATE(),
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE forgotPassword (
    fpid BIGINT IDENTITY(1,1) PRIMARY KEY,
    otp INT NOT NULL,
    expiration_time DATETIME NOT NULL,
    [user_id] BIGINT NOT NULL,
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
	('SHIFT001', 'Morning Shift', '08:00', '16:00', 'PART_TIME',  1, 1  ),
    ('SHIFT002', 'Afternoon Shift', '16:00', '00:00', 'PART_TIME', 2, 2),
    ('SHIFT003', 'Night Shift', '00:00', '08:00', 'PART_TIME', 3, 3);


GO
alter table zones drop column product_name
go

	alter table invoice_details add created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME NOT NULL DEFAULT GETDATE(),
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BIT DEFAULT 0
	go

CREATE TABLE customer_change_histories (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    changed_field NVARCHAR(255) NOT NULL,
    old_value NVARCHAR(255),
    new_value NVARCHAR(255),
    additional_info NVARCHAR(255),
    changed_by BIGINT NOT NULL,
    changed_at DATETIME2 NOT NULL,
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
UPDATE STATISTICS invoices;
UPDATE STATISTICS invoice_details;
UPDATE STATISTICS debt_records;

	


