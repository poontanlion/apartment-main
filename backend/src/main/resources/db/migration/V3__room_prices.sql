CREATE TABLE room_prices (
    id varchar(255) NOT NULL,
    room_id varchar(255) NOT NULL,
    price double precision NOT NULL,
    effective_month date NOT NULL,
    created_at timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_room_prices_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT uk_room_prices_room_month UNIQUE (room_id, effective_month)
);

ALTER TABLE leases ADD COLUMN terminated_date date;
