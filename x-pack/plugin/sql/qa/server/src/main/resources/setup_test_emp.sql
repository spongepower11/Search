DROP TABLE IF EXISTS "test_emp";
CREATE TABLE "test_emp" (
                    "birth_date" TIMESTAMP WITH TIME ZONE,
                    "birth_date_day_of_week" VARCHAR(50),
                    "emp_no" INT, 
                    "first_name" VARCHAR(50),
                    "gender" VARCHAR(1),
                    "hire_date" TIMESTAMP WITH TIME ZONE,
                    "languages" TINYINT,
                    "last_name" VARCHAR(50),
                    "salary" INT
                   )
   AS SELECT * FROM CSVREAD('classpath:/employees.csv');