-- Ensure default tables exist
CREATE TABLE IF NOT EXISTS students (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    enrollment_year INT NOT NULL,
    profile VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS employees (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    hire_date DATE NOT NULL,
    profile VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS banks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bank_name VARCHAR(255) NOT NULL,
    branch_code VARCHAR(50) NOT NULL,
    location VARCHAR(255) NOT NULL
);

-- Attempt to add profile column to existing tables (will error safely if it already exists due to spring.sql.init.continue-on-error=true)
ALTER TABLE students ADD COLUMN profile VARCHAR(255);
ALTER TABLE employees ADD COLUMN profile VARCHAR(255);
