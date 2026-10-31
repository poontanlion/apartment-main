CREATE TABLE activity_logs (
    created_at timestamp(6),
    details varchar(1000),
    action varchar(255),
    id varchar(255) NOT NULL,
    user_id varchar(255),
    user_name varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE app_notifications (
    is_read boolean,
    created_at timestamp(6),
    message varchar(1000),
    id varchar(255) NOT NULL,
    title varchar(255),
    type varchar(255),
    user_id varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE bookings (
    guest_count integer,
    total_price double precision,
    created_at timestamp(6),
    booking_no varchar(255),
    check_in varchar(255),
    check_out varchar(255),
    guest_email varchar(255),
    guest_name varchar(255),
    guest_phone varchar(255),
    id varchar(255) NOT NULL,
    lease_id varchar(255),
    room_id varchar(255),
    room_number varchar(255),
    special_requests text,
    status varchar(255),
    tenant_id varchar(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_bookings_booking_no UNIQUE (booking_no)
);

CREATE TABLE buildings (
    floors integer,
    total_rooms integer,
    created_at timestamp(6),
    address varchar(1000),
    cover_image varchar(1000),
    description varchar(1000),
    code varchar(255) NOT NULL,
    id varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE leases (
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    deposit_amount double precision,
    rent_amount double precision,
    created_at timestamp(6),
    billing_cycle varchar(255),
    id varchar(255) NOT NULL,
    notes text,
    room_id varchar(255) NOT NULL,
    room_number varchar(255),
    status varchar(255),
    tenant_id varchar(255) NOT NULL,
    tenant_name varchar(255),
    tenant_phone varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE maintenance_logs (
    total_cost double precision,
    description varchar(1000),
    category varchar(255),
    date varchar(255),
    id varchar(255) NOT NULL,
    performed_by varchar(255),
    room_id varchar(255),
    room_number varchar(255),
    supplies_summary text,
    task_no varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE maintenance_tasks (
    archived boolean,
    labor_cost double precision,
    total_cost double precision,
    completed_at timestamp(6),
    created_at timestamp(6),
    description varchar(1000),
    supplies_used varchar(2000),
    assigned_worker varchar(255),
    category varchar(255),
    due_date varchar(255),
    id varchar(255) NOT NULL,
    occupancy_type varchar(255),
    preferred_time varchar(255),
    priority varchar(255),
    recurring_reminder varchar(255),
    reported_date varchar(255),
    reporter_email varchar(255),
    reporter_name varchar(255),
    reporter_phone varchar(255),
    room_id varchar(255),
    room_number varchar(255),
    status varchar(255),
    task_no varchar(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_maintenance_tasks_task_no UNIQUE (task_no)
);

CREATE TABLE rooms (
    capacity integer,
    curr_electric_meter double precision,
    curr_water_meter double precision,
    floor integer NOT NULL,
    prev_electric_meter double precision,
    prev_water_meter double precision,
    price double precision,
    size_sqm double precision,
    created_at timestamp(6),
    amenities text,
    bed_type varchar(255),
    building_id varchar(255),
    building_name varchar(255),
    cover_image text,
    current_tenant_id varchar(255),
    current_tenant_name varchar(255),
    description text,
    gallery text,
    id varchar(255) NOT NULL,
    room_name varchar(255),
    room_number varchar(255) NOT NULL,
    room_type varchar(255),
    status varchar(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_room_number UNIQUE (room_number)
);

CREATE TABLE scheduled_reminders (
    is_active boolean,
    category varchar(255),
    frequency varchar(255),
    id varchar(255) NOT NULL,
    last_triggered varchar(255),
    next_due_date varchar(255),
    room_id varchar(255),
    room_number varchar(255),
    title varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE supply_items (
    base_stock_quantity integer,
    stock_quantity integer,
    unit_cost double precision,
    category varchar(255),
    id varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    unit_name varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE tenants (
    created_at timestamp(6),
    email varchar(255),
    emergency_contact varchar(255),
    fullname varchar(255) NOT NULL,
    id varchar(255) NOT NULL,
    id_card_passport varchar(255),
    phone varchar(255),
    unit_id varchar(255),
    unit_number varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE users (
    created_at timestamp(6),
    last_password_change timestamp(6),
    email varchar(255) NOT NULL,
    fullname varchar(255),
    id varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    phone varchar(255),
    role varchar(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE utility_bills (
    common_fee double precision,
    curr_electric_meter double precision,
    curr_water_meter double precision,
    electric_amount double precision,
    electric_rate double precision,
    prev_electric_meter double precision,
    prev_water_meter double precision,
    rent_amount double precision,
    total_amount double precision,
    water_amount double precision,
    water_rate double precision,
    created_at timestamp(6),
    billing_month varchar(255),
    id varchar(255) NOT NULL,
    invoice_no varchar(255),
    lease_id varchar(255),
    payment_date varchar(255),
    room_id varchar(255),
    room_number varchar(255),
    slip_image text,
    status varchar(255),
    tenant_name varchar(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_utility_bills_invoice_no UNIQUE (invoice_no)
);

CREATE INDEX idx_rooms_building_id ON rooms (building_id);
CREATE INDEX idx_rooms_status ON rooms (status);
CREATE INDEX idx_bookings_room_id_status ON bookings (room_id, status);
CREATE INDEX idx_bookings_created_at ON bookings (created_at);
CREATE INDEX idx_leases_room_id_status ON leases (room_id, status);
CREATE INDEX idx_maintenance_logs_room_id ON maintenance_logs (room_id);
CREATE INDEX idx_maintenance_logs_task_no ON maintenance_logs (task_no);
CREATE INDEX idx_maintenance_tasks_room_number ON maintenance_tasks (room_number);
CREATE INDEX idx_activity_logs_created_at ON activity_logs (created_at);
