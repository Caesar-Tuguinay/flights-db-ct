# Database Class Assignment to Implement Flights Booking System with Transactions

## Important Note:

#### The following links lead to code and design that were done by me:

Link to Query and Transaction Management Code: [Query Code](https://github.com/ctuguinay/Flights-Database-CT/blob/14a8980ee50846d47ecf3a432c36b69668db59c5/src/main/java/flightapp/Query.java)

Link to Test Cases Code: [Cases](github.com/ctuguinay/Flights-Database-CT/tree/master/cases)

Link to ER Diagram/DB Design: [Design](github.com/ctuguinay/Flights-Database-CT/blob/master/design.md)

#### The rest of the code was provided in the template for the HW assignment.

## How to Run Locally:

* Make sure your are at the top of the repository. 
* This first command will package files and dependencies into a single jar file:
```
$ mvn clean compile assembly:single
```

* This second command will run the main method from `FlightService.java` (not my own code), which uses the interface logic for `Query.java` (my own code):
```
$ java -jar target/FlightApp-1.0-jar-with-dependencies.jar
```

* You should get the following in your UI:
```
*** Please enter one of the following commands ***
> create <username> <password> <initial amount>
> login <username> <password>
> search <origin city> <destination city> <direct> <day> <num itineraries>
> book <itinerary id>
> pay <reservation id>
> reservations
> cancel <reservation id>
> quit
```
