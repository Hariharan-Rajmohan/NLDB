-- Clear existing data to avoid duplicate inserts on restart
DELETE FROM students;
DELETE FROM employees;

-- Students seed data
INSERT INTO students (name, department, enrollment_year) VALUES ('Aarav Sharma', 'Computer Science', 2022);
INSERT INTO students (name, department, enrollment_year) VALUES ('Priya Patel', 'Cyber Security', 2023);
INSERT INTO students (name, department, enrollment_year) VALUES ('Rahul Verma', 'Computer Science', 2021);
INSERT INTO students (name, department, enrollment_year) VALUES ('Sneha Gupta', 'Data Science', 2023);
INSERT INTO students (name, department, enrollment_year) VALUES ('Vikram Singh', 'Cyber Security', 2022);
INSERT INTO students (name, department, enrollment_year) VALUES ('Ananya Reddy', 'Artificial Intelligence', 2024);
INSERT INTO students (name, department, enrollment_year) VALUES ('Karthik Nair', 'Data Science', 2021);
INSERT INTO students (name, department, enrollment_year) VALUES ('Divya Joshi', 'Computer Science', 2024);
INSERT INTO students (name, department, enrollment_year) VALUES ('Arjun Mehta', 'Artificial Intelligence', 2022);
INSERT INTO students (name, department, enrollment_year) VALUES ('Meera Iyer', 'Cyber Security', 2023);
INSERT INTO students (name, department, enrollment_year) VALUES ('Rohan Kumar', 'Data Science', 2024);
INSERT INTO students (name, department, enrollment_year) VALUES ('Ishita Das', 'Computer Science', 2021);

-- Employees seed data
INSERT INTO employees (name, department, hire_date) VALUES ('Suresh Menon', 'Engineering', '2019-03-15');
INSERT INTO employees (name, department, hire_date) VALUES ('Kavita Rao', 'Human Resources', '2020-07-01');
INSERT INTO employees (name, department, hire_date) VALUES ('Amit Banerjee', 'Engineering', '2018-11-20');
INSERT INTO employees (name, department, hire_date) VALUES ('Lakshmi Pillai', 'Marketing', '2021-01-10');
INSERT INTO employees (name, department, hire_date) VALUES ('Rajesh Kulkarni', 'Finance', '2017-06-05');
INSERT INTO employees (name, department, hire_date) VALUES ('Pooja Desai', 'Engineering', '2022-04-18');
INSERT INTO employees (name, department, hire_date) VALUES ('Nikhil Bhat', 'Marketing', '2020-09-30');
INSERT INTO employees (name, department, hire_date) VALUES ('Swati Mishra', 'Human Resources', '2019-12-12');
INSERT INTO employees (name, department, hire_date) VALUES ('Deepak Tiwari', 'Finance', '2023-02-28');
INSERT INTO employees (name, department, hire_date) VALUES ('Anjali Saxena', 'Engineering', '2021-08-14');
