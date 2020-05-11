# Java-Sales-DB_Reconcile
This Program reads the reconciles the sales database and links orders to payments.

Several operations are conducted on the database and comes with output. Here are the functions written to deal with those scenarios:
•	reconcile payments() Method: This method alters the database and links all the order rows and payment rows to each other comparing the scenario of one payment one order and also many orders one payments by satisfying all the constraints like maintaining the payments chronologically.
•	pay order() Method: This method takes a list of order numbers, amount, check number and object of connection as arguments and updates the check number for the given order number if the given conditions satisfy.
•	unpaid orders() Method: This method returns the list of unmapped order numbers after all reconciling the database.
•	unknown payments() Method: This method returns the list of payment numbers after reconciling the database.

Files and External data: 
	This program needs an external SQL file that alters the database schema. This file should be run before loading the class.
	This program also accesses the sales database and changes that.

Classes:
1.	PaymentManagment:
•	This class has four methods as given in the problem statement.
•	This class takes need a valid database connection object to perform its operations.

Database Used:
	An existing MySql database is used to complete the problem statement.
	Queries of MySql were also been used to perform database operations.
Assumptions:
	No past order was partially paid, and no past order was paid in installments.


