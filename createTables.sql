create table Users(
    username varchar(20) PRIMARY KEY,
    hash varbinary(1000),
    salt varbinary(1000),
    balance int);

create table PreItineraries(
    fid_one int,
    fid_two int,
    total_time int,
    num_flights int);

create table Reservations(
    re_id int IDENTITY(1,1) PRIMARY KEY,
    fid_one int,
    fid_two int,
    total_price int,
    username varchar(20),
    pay int
);

create table Cancelled(
    re_id_cancelled int PRIMARY KEY,
);

