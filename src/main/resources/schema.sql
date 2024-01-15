drop table if exists post;

create table post
(
    id bigserial not null,
    title varchar(255) not null,
    slug varchar(255) not null,
    date date not null,
    time_to_read int not null,
    tags varchar(255),
    primary key (id)
);

insert into post (id, title, slug, date, time_to_read, tags) values
(1, 'My First Post', 'my-first-post', '2020-01-01', 1, 'test, test2');