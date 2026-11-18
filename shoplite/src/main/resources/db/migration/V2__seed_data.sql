-- Demo users (BCrypt hashes): alice/alice123 and bob/bob123 for functional tests,
-- shopper/shopper123 for load tests so perf traffic never pollutes functional test data.
INSERT INTO users (username, password_hash) VALUES
    ('alice',   '$2a$10$QG/lLbzgizNKK0EoQCFso..TuUTMWpBW/W/8Um0yhtXKG/KLrLkeG'),
    ('bob',     '$2a$10$VOaRxb/GThfC0br0nMRukO1fvy6zXhklsyQPohWyqKHXvueQ5Re6K'),
    ('shopper', '$2a$10$eNU0SGnCeZgRZe1qISNx0eaTjNdQm5Tkc0/jPwVW15FuBypLwO/y6');

INSERT INTO products (name, description, price, stock) VALUES
    ('Coffee Mug',             'Ceramic mug, 350 ml',                  12.99,     500),
    ('Travel Mug',             'Insulated steel mug, 450 ml',          24.50,     300),
    ('Notebook',               'A5 dotted notebook, 120 pages',         7.25,    1000),
    ('Mechanical Keyboard',    'Tenkeyless, brown switches',           89.00,     150),
    ('Wireless Mouse',         'Ergonomic, USB-C rechargeable',        29.99,     400),
    ('Desk Plant',             'Low-maintenance succulent',            15.00,     800),
    ('Limited Edition Poster', 'Signed print, only a few left',        49.00,       5),
    ('Sticker Pack',           'Ten developer stickers',                3.50, 1000000);
