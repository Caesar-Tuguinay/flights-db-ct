package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  // For check dangling
  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCountStatement;

  // Saves a boolean value to express whether or not a user is logged in; true if logged in, false if not
  private boolean logged_in = false;
  
  // Saves the user's userID info as a string. The string is left as null until the user logs in
  private String user_id = null;
  
  // Saves the user's itineraries
  HashMap<Integer, int[]> MapItineraries = new HashMap<Integer, int[]>();
  
  // Clears all the entries in the custom tables
  private static final String CLEAR_TABLES = "DELETE FROM Users; DELETE FROM PreItineraries; DELETE FROM Reservations; DELETE FROM Cancelled;";
  private PreparedStatement clearTablesStatement;
  
  // Clears the Itineraries tables
  private static final String CLEAR_ITINERARIES = "DELETE FROM PreItineraries;";
  private PreparedStatement clearItinerariesStatement;
  
  // Checks if the username already exists
  private static final String USER_EXISTS = "SELECT COUNT(U.username) AS username_count FROM Users AS U WHERE LOWER(U.username) = ?;";
  private PreparedStatement userExistsStatement;
  
  // Fineds the hash for the associated user
  private static final String HASH_USER = "SELECT U.hash AS username_hash FROM Users AS U WHERE LOWER(U.username) = ?;";
  private PreparedStatement hashUserStatement;
  
  // Retrieve salt associated for the associated user
  private static final String RETRIEVE_SALT = "SELECT U.salt AS user_salt FROM Users AS U WHERE LOWER(U.username) = ?;";
  private PreparedStatement retrieveSaltStatement;
  
  // Makes new account for the new user
  private static final String ACCOUNT_CREATION = "INSERT INTO Users (username, hash, salt, balance) VALUES (?,?,?,?);";
  private PreparedStatement accountCreationStatement;
  
  // Finds one flight itineraries
  private static final String ONE_FLIGHT = "SELECT TOP (?) f.fid AS fid_one, f.actual_time AS total_time FROM Flights AS f WHERE f.origin_city = ? AND f.dest_city = ? " +
                                           "AND f.day_of_month =  ? AND f.canceled = 0 ORDER BY f.actual_time ASC;";
  private PreparedStatement oneFlightStatement;
  
  // Finds two flight itineraries
  private static final String TWO_FLIGHT = "SELECT TOP (?) f.fid AS fid_one, g.fid AS fid_two, g.actual_time + f.actual_time AS total_time FROM Flights AS f, Flights AS g " + 
                                           "WHERE f.origin_city = ? AND f.dest_city = g.origin_city AND g.dest_city = ? AND f.day_of_month = ? " +
                                           "AND g.day_of_month = ? AND f.canceled = 0 AND g.canceled = 0 ORDER BY f.actual_time + g.actual_time ASC;";
  private PreparedStatement twoFlightStatement;
  
  // Insert into Pre-Itineraries table
  private static final String INSERT_PRE = "INSERT INTO PreItineraries (fid_one, fid_two, total_time, num_flights) VALUES (?,?,?,?);";
  private PreparedStatement insertPreStatement;
  
  // Pull top values for direct flight from Pre-Itineraries table
  private static final String PULL_DIRECT = "SELECT TOP (?) p.fid_one AS fid_one, p.fid_two AS fid_two, p.total_time AS total_time, p.num_flights AS num_flights FROM PreItineraries AS p " +
                                            "WHERE p.num_flights = 1 ORDER BY p.num_flights DESC, p.total_time ASC, p.fid_one ASC, p.fid_two ASC;";
  private PreparedStatement pullDirectStatement;
  
  // Get a count for number of direct flights from Pre-Itineraries table
  private static final String PULL_COUNT = "SELECT COUNT(*) AS count FROM PreItineraries AS p WHERE p.num_flights = 1";
  private PreparedStatement pullCountStatement;
  
  // Pull top values for indirect flight from Pre-Itineraries table
  private static final String PULL_INDIRECT = "SELECT TOP (?) p.fid_one AS fid_one, p.fid_two AS fid_two, p.total_time AS total_time, p.num_flights AS num_flights FROM PreItineraries AS p " +
                                         "WHERE p.num_flights = 2 ORDER BY p.num_flights DESC, p.total_time ASC, p.fid_one ASC, p.fid_two ASC;";
  private PreparedStatement pullIndirectStatement;
  
  // Get values associated with fid value
  private static final String FID_VALUES = "SELECT f.carrier_id AS carrier_id, f.origin_city AS origin_city, f.dest_city AS dest_city, f.actual_time AS actual_time, f.capacity AS capacity, " +
                                           "f.price as price, f.flight_num AS flight_num, f.day_of_month AS day_of_month FROM Flights AS f WHERE f.fid = ?;";
  private PreparedStatement fidValuesStatement;

  // Returns available seats for a specific flight
  private static final String CHECK_FULL = "WITH Mod AS (SELECT COUNT(*) AS count FROM Reservations AS r WHERE (r.fid_one = ? OR r.fid_two = ?)) " + 
                                           "SELECT f.capacity - m.count  AS seats_left FROM Mod AS m, Flights AS f WHERE f.fid = ?";
  private PreparedStatement checkFullStatement;
  
  // Finds number of flights booked by the user on the same day
  private static final String COUNT_SAME = "SELECT COUNT(*) AS same_day from Reservations AS r, Flights AS f, Flights AS g WHERE f.fid = ? AND r.username = ? " +
                                            "AND g.fid = r.fid_one AND g.day_of_month = f.day_of_month";
  private PreparedStatement countSameStatement;
  
  // Finds cost of a flight
  private static final String FLIGHT_COST = "SELECT f.price AS flight_cost FROM Flights AS f WHERE f.fid = ?";
  private PreparedStatement flightCostStatement;
  
  // Books flights for the user based on intinerary number
  private static final String BOOK_ITINERARY = "INSERT INTO Reservations (fid_one, fid_two, total_price, username, pay) VALUES(?, ?, ?, ?, ?);";
  private PreparedStatement bookItineraryStatement;
  
  // Finds max value (user's value) from reservations ID, which is the same value as the number of rows in both Reservations and Cancelled reservations
  private static final String FIND_MAX = "WITH MOD_R AS (SELECT COUNT(*) AS count_r FROM Reservations), MOD_C AS (SELECT COUNT(*) AS count_c FROM Cancelled) " +
                                         "SELECT r.count_r + c.count_c as count_id FROM MOD_R AS r, MOD_C AS c;";
  private PreparedStatement findMaxStatement;
  
  // Checks if there exists an unpaid reservation with a specific ID under the user's name
  private static final String CHECK_RESERVATION = "SELECT COUNT(*) as number_reservations FROM Reservations AS r WHERE r.username = ? AND r.re_id = ? AND r.pay = 0;";
  private PreparedStatement checkReservationStatement;
  
  // Checks if the user can pay for the reservation
  private static final String CHECK_MONEY = "SELECT u.balance - r.total_price AS balance_after, u.balance AS user_balance, r.total_price AS total_price FROM Reservations AS r, Users AS u " +
                                            "WHERE u.username = ? AND r.username = ? AND r.re_id = ?";
  private PreparedStatement checkMoneyStatement;
  
  // Officially pays for the reservation
  private static final String PAY_RESERVATION = "UPDATE Reservations SET pay = 1 WHERE username = ? AND re_id = ?";
  private PreparedStatement payReservationStatement;
  
  // Update user's money after successful payment
  private static final String LESS_MONEY = "UPDATE Users SET balance = ? WHERE username = ?";
  private PreparedStatement lessMoneyStatement;
  
  // Counts numbers of total row in Reservations and for Cancelled reservations
  private static final String TOTAL_RESERVATIONS = "SELECT COUNT(*) AS row_count FROM Reservations";
  private PreparedStatement totalReservationsStatement;
  
  // Reindex Reservations re_id column to 0
  private static final String REINDEX_RESERVATIONS = "DBCC CHECKIDENT('Reservations', RESEED, 0);";
  private PreparedStatement reindexReservationsStatement;
  
  // Find number of reservations for the user 
  private static final String COUNT_RESERVATIONS = "SELECT COUNT(*) AS reservation_count FROM Reservations AS r WHERE r.username = ?";
  private PreparedStatement countReservationsStatement;
  
  // Gets all reservations for the user
  private static final String USER_RESERVATIONS = "SELECT r.re_id AS re_id, r.fid_one AS fid_one, r.fid_two AS fid_two, r.pay AS pay FROM Reservations AS r WHERE r.username = ?";
  private PreparedStatement userReservationsStatement;
  
  // Find if the user has a reservation with a particular ID
  private static final String SPECIFIC_RESERVATION = "SELECT COUNT(*) as count_reservations FROM Reservations AS r WHERE r.username = ? AND r.re_id = ?";
  private PreparedStatement specificReservationStatement;  
  
  // Gets info regarding the reservation and the user which the reservation belongs to
  private static final String RESERVATION_INFO = "SELECT r.pay AS pay, r.total_price AS total_price, u.balance AS user_balance FROM Reservations AS r, " + 
                                                 "Users AS u WHERE u.username = ? AND r.username = ? AND r.re_id = ?";
  private PreparedStatement reservationInfoStatement;
    
  // Cancels the reservation
  private static final String CANCEL_RESERVATION = "DELETE FROM Reservations WHERE username = ? AND re_id = ?";
  private PreparedStatement cancelReservationStatement;
  
  // Adds cancelled reservation's id into the Cancelled reservations table
  private static final String ADD_CANCELLED = "INSERT INTO Cancelled (re_id_cancelled) VALUES (?);";
  private PreparedStatement addCancelledStatement;
  
  // Refunds reservation if reservation was paid
  private static final String REFUND_USER = "UPDATE Users SET balance = ? WHERE username = ?";
  private PreparedStatement refundUserStatement;
  
  public Query() throws SQLException, IOException {
    this(null, null, null, null);
  }

  protected Query(String serverURL, String dbName, String adminName, String password)
      throws SQLException, IOException {
    conn = serverURL == null ? openConnectionFromDbConn()
        : openConnectionFromCredential(serverURL, dbName, adminName, password);

    prepareStatements();
  }

  /**
   * Return a connecion by using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnectionFromDbConn() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("flightapp.server_url");
    String dbName = configProps.getProperty("flightapp.database_name");
    String adminName = configProps.getProperty("flightapp.username");
    String password = configProps.getProperty("flightapp.password");
    return openConnectionFromCredential(serverURL, dbName, adminName, password);
  }

  /**
   * Return a connecion by using the provided parameter.
   *
   * @param serverURL example: example.database.widows.net
   * @param dbName    database name
   * @param adminName username to login server
   * @param password  password to login server
   *
   * @throws SQLException
   */
  protected static Connection openConnectionFromCredential(String serverURL, String dbName,
      String adminName, String password) throws SQLException {
    String connectionUrl =
        String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
            dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    return conn;
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
   public void clearTables() {
   
      try {
      
         // Finds number of rows in Reservations table and Cancelled table
         
         try {
         
            findMaxStatement.clearParameters();
            ResultSet findMaxResultSet = findMaxStatement.executeQuery();
            findMaxResultSet.next();
            int row_count = findMaxResultSet.getInt("count_id");
            
            // Clears tables
            
            try {
            
               clearTablesStatement.clearParameters(); 
               clearTablesStatement.executeUpdate();
               
               // Resets index of Reservations table if Reservations or Cancelled previously had rows inserted into it
               
               if (row_count > 0) {
                  
                  try {
                  
                     reindexReservationsStatement.clearParameters();
                     reindexReservationsStatement.executeUpdate();
                     
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                     
                  }
                  
               }
               
            } catch (SQLException e) {
            
               e.printStackTrace();
            } 
            
         } catch (Exception e) {
         
            e.printStackTrace();
            
         }
         
      } finally {
      
         checkDanglingTransaction();
      
      }
   }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
    clearTablesStatement = conn.prepareStatement(CLEAR_TABLES);
    clearItinerariesStatement = conn.prepareStatement(CLEAR_ITINERARIES);
    userExistsStatement = conn.prepareStatement(USER_EXISTS);
    hashUserStatement = conn.prepareStatement(HASH_USER);
    retrieveSaltStatement = conn.prepareStatement(RETRIEVE_SALT);
    accountCreationStatement = conn.prepareStatement(ACCOUNT_CREATION);
    oneFlightStatement = conn.prepareStatement(ONE_FLIGHT);
    twoFlightStatement = conn.prepareStatement(TWO_FLIGHT);
    insertPreStatement = conn.prepareStatement(INSERT_PRE);
    pullDirectStatement = conn.prepareStatement(PULL_DIRECT);
    pullCountStatement = conn.prepareStatement(PULL_COUNT);
    pullIndirectStatement = conn.prepareStatement(PULL_INDIRECT);
    fidValuesStatement = conn.prepareStatement(FID_VALUES);
    checkFullStatement = conn.prepareStatement(CHECK_FULL);
    countSameStatement = conn.prepareStatement(COUNT_SAME);
    bookItineraryStatement = conn.prepareStatement(BOOK_ITINERARY);
    flightCostStatement = conn.prepareStatement(FLIGHT_COST);
    findMaxStatement = conn.prepareStatement(FIND_MAX);
    checkReservationStatement = conn.prepareStatement(CHECK_RESERVATION);
    checkMoneyStatement = conn.prepareStatement(CHECK_MONEY);
    payReservationStatement = conn.prepareStatement(PAY_RESERVATION);
    lessMoneyStatement = conn.prepareStatement(LESS_MONEY);
    totalReservationsStatement = conn.prepareStatement(TOTAL_RESERVATIONS);
    reindexReservationsStatement = conn.prepareStatement(REINDEX_RESERVATIONS);
    countReservationsStatement = conn.prepareStatement(COUNT_RESERVATIONS);
    userReservationsStatement = conn.prepareStatement(USER_RESERVATIONS);
    specificReservationStatement = conn.prepareStatement(SPECIFIC_RESERVATION);
    reservationInfoStatement = conn.prepareStatement(RESERVATION_INFO);
    cancelReservationStatement = conn.prepareStatement(CANCEL_RESERVATION);
    addCancelledStatement = conn.prepareStatement(ADD_CANCELLED);
    refundUserStatement = conn.prepareStatement(REFUND_USER);

  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
   public String transaction_login(String username, String password) {
   
      try {
      
         // Return already logged in message if user already logged in
         
         if (logged_in) {
         
           return "User already logged in\n";
           
         }
         
         // Clear pre-itinerary table
         
         try {
      
            clearItinerariesStatement.clearParameters();
            clearItinerariesStatement.executeUpdate();
            
         } catch (SQLException e) {
         
            e.printStackTrace();
            
         }
         
         try {
       
            // Retrieve salt for the given username
            
            retrieveSaltStatement.clearParameters();
            retrieveSaltStatement.setString(1, username.toLowerCase());
            ResultSet rs = retrieveSaltStatement.executeQuery();
         
            if (!rs.next()){
            
               return "Login failed\n";
               
            }
            
            byte[] salt = rs.getBytes("user_salt");
            
            // Specify the hash parameters
            
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
         
            try {
         
               // Generate the hash from the salt and the password
               
               SecretKeyFactory factory = null;
               byte[] hash = null;
               factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
               hash = factory.generateSecret(spec).getEncoded();
               
               try {
            
                  // After hash has been generated, check if the hash is equal to the hash stored by the user
                  
                  hashUserStatement.clearParameters();
                  hashUserStatement.setString(1, username.toLowerCase());
                  ResultSet rs_two = hashUserStatement.executeQuery();
                  rs_two.next();
                  byte[] username_hash = rs_two.getBytes("username_hash");
               
                  // Login if the byte array in the Users table matches the hash
                  // Update class variables user_id and logged_in if login is successful
                  
                  if (Arrays.equals(username_hash, hash)) {
                     user_id = username.toLowerCase();
                     logged_in = true;
                     return "Logged in as " + username + "\n";
                     
                  }
                  
               } catch (SQLException e) {
                  
                  e.printStackTrace();
               }
               
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
               
               throw new IllegalStateException();
            }
            
         } catch (SQLException e) {
            
            e.printStackTrace();
         }
         
         return "Login failed\n";
         
      } finally {
      
         checkDanglingTransaction();
         
      }
   }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
   public String transaction_createCustomer(String username, String password, int initAmount) {
   
      try {
            
         // If initial amount is a negative number, then return a failure message
         
         if (initAmount < 0) {
         
            return "Failed to create user\n";
            
         }
         
         // Set auto commit to false
         
         for (int i = 0; i < 3; i++) {
         
            try {
            
               conn.setAutoCommit(false);
            
               try {
               
                  // Check if username already exists (non-case sensitive)
                  
                  String mod_username = username.toLowerCase();
                  userExistsStatement.clearParameters();
                  userExistsStatement.setString(1, mod_username);
                  ResultSet rs = userExistsStatement.executeQuery();
                  rs.next();
                  int username_count = rs.getInt("username_count");
                  
                  if (username_count != 0) {
                     
                     try {
                     
                        conn.rollback();
                     
                     } catch (SQLException e) {
                     
                        e.printStackTrace();
                        
                     }
                     return "Failed to create user\n";
                     
                  }
                  
                  // Generate a random cryptographic salt
                  
                  SecureRandom random = new SecureRandom();
                  byte[] salt = new byte[16];
                  random.nextBytes(salt);
              
                  // Specify the hash parameters
                  
                  KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
                  
                  // Generate the hash
                  
                  SecretKeyFactory factory = null;
                  byte[] hash = null;
                  
                  // Insert username, hash, and salt into the User table
                                    
                  try {
                  
                  factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                  hash = factory.generateSecret(spec).getEncoded();
                  
                     try {
                     
                        accountCreationStatement.clearParameters();
                        accountCreationStatement.setString(1, username);
                        accountCreationStatement.setBytes(2, hash);
                        accountCreationStatement.setBytes(3, salt);
                        accountCreationStatement.setInt(4, initAmount);
                        accountCreationStatement.executeUpdate();
                        conn.commit();
                        conn.setAutoCommit(true);
                        return "Created user " + username + "\n";
                        
                     } catch (SQLException e) {
                     
                        conn.rollback();
                        
                        // Since terminals will obviously clash in the first few iterations of the transaction, 
                        // we only want to know when the terminals are still clashing in later iterations.
                        
                        if (i == 3) {
                        
                           e.printStackTrace(); 
                           
                        }
                     }  
                     
                  } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                  
                     throw new IllegalStateException();
                  
                  }
                  
               } catch (SQLException e) {
                  
                  e.printStackTrace();
               
               }
                
            } catch (SQLException e) {
               
               e.printStackTrace();
            
            }
         
         }
         
         try {
         
            conn.rollback();
         
         } catch (SQLException e) {
         
            e.printStackTrace();
         
         }
         
         return "Failed to create user\n";
         
      } finally {
         
         checkDanglingTransaction(); 
      
      }
   
   }
  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
      int dayOfMonth, int numberOfItineraries) {
      
      try {
         
         // Clear up all stored itineraries
         
         MapItineraries.clear();
      
         // Set autocommit to false
         
         try {
         
            conn.setAutoCommit(false);
      
            // Initialize a string buffer object to help store the itinerary return values
            
            StringBuffer sb = new StringBuffer();
            
            // Clear itineraries tables
            
            try {
         
               clearItinerariesStatement.clearParameters();
               clearItinerariesStatement.executeUpdate();
               
            } catch (SQLException e) {
            
               e.printStackTrace();
               
            }
         
            // Insert direct flights into PreIntinerary table to help organize which flights the user actually wants
            
            try {
            
               // Find direct flights
               
               oneFlightStatement.clearParameters();
               oneFlightStatement.setInt(1, numberOfItineraries);
               oneFlightStatement.setString(2, originCity);
               oneFlightStatement.setString(3, destinationCity);
               oneFlightStatement.setInt(4, dayOfMonth);
               ResultSet rs = oneFlightStatement.executeQuery();
               while (rs.next()) {
               
                  // Insert direct flights one by one into the PreIntinerary table
                  
                  try {
                     int fid_one = rs.getInt("fid_one");
                     int total_time = rs.getInt("total_time");
                     insertPreStatement.clearParameters();
                     insertPreStatement.setInt(1, fid_one);
                     
                     // We set fid_two to -1 to indicate that it is a direct flight and that the value should largely be ignored
                     
                     insertPreStatement.setInt(2, -1);
                     insertPreStatement.setInt(3, total_time);
                     insertPreStatement.setInt(4, 1);
                     insertPreStatement.executeUpdate();
                  } catch (SQLException e) {
                     e.printStackTrace();
                  }
               }
               
               rs.close();
               
               // Insert indirect flights into the preintinerary table to help organize which flights the user wants, if direct flights is false
               
               if (!directFlight) {
               
                  try {
                  
                     // Find indirect flights
                     
                     twoFlightStatement.clearParameters();
                     twoFlightStatement.setInt(1, numberOfItineraries);
                     twoFlightStatement.setString(2, originCity);
                     twoFlightStatement.setString(3, destinationCity);
                     twoFlightStatement.setInt(4, dayOfMonth);
                     twoFlightStatement.setInt(5, dayOfMonth);
                     ResultSet rs_two = twoFlightStatement.executeQuery();
                     
                     while (rs_two.next()) {
                     
                        try {
                        
                           // Insert each indirect flight into the PreIntinerary table one by one
                           
                           int fid_one = rs_two.getInt("fid_one");
                           int fid_two = rs_two.getInt("fid_two");
                           int total_time = rs_two.getInt("total_time");
                           insertPreStatement.clearParameters();
                           insertPreStatement.setInt(1, fid_one);
                           insertPreStatement.setInt(2, fid_two);
                           insertPreStatement.setInt(3, total_time);
                           insertPreStatement.setInt(4, 2);
                           insertPreStatement.executeUpdate();
                           
                        } catch (SQLException e) {
                           e.printStackTrace();
                        }
                     }
                     rs_two.close();
                  } catch (SQLException e) {
                     e.printStackTrace();
                  }
               }
            } catch (SQLException e) {
               e.printStackTrace();
            }
            
            
            try {
            
               // Sets the counter which will be used to keep track of itinerary number
             
               int count = 0;
             
               // Place indirect flight information into the StringBuffer object (if directFlight is false)
               // Only places the number of indirect flights that are needed due to a deficit of direct flights
               // or in other words (number of requested intineraries - direct flight number).
             
               if (!directFlight) {
             
                  try {
             
                     // Finds number of direct flights
             
                     pullCountStatement.clearParameters();
                     ResultSet rs_count = pullCountStatement.executeQuery();
                     rs_count.next();
                     int num_direct = rs_count.getInt("count");
                     int num_indirect = numberOfItineraries - num_direct;
                     
                     try {
                     
                        // Find indirect flights
             
                        pullIndirectStatement.clearParameters();
                        pullIndirectStatement.setInt(1, num_indirect);
                        ResultSet rs_three = pullIndirectStatement.executeQuery();
                        
                        while (rs_three.next()) {
                        
                           // Find itinerary information
             
                           int fid_one = rs_three.getInt("fid_one");
                           int fid_two = rs_three.getInt("fid_two");
                           int total_time = rs_three.getInt("total_time");
                           int num_flights = rs_three.getInt("num_flights");
                           sb.append("Itinerary " + count + ": " + num_flights + " flight(s), " + total_time + " minutes\n");
                           
                           // Find flight values for fid_one and place in string buffer
             
                           try {
                           
                              fidValuesStatement.clearParameters();
                              fidValuesStatement.setInt(1, fid_one);
                              ResultSet rs_fid_one = fidValuesStatement.executeQuery();
                              rs_fid_one.next();
                              String result_carrierId = rs_fid_one.getString("carrier_id");
                              String result_flightNum = rs_fid_one.getString("flight_num");
                              String result_originCity = rs_fid_one.getString("origin_city");
                              String result_destCity = rs_fid_one.getString("dest_city");
                              int result_time = rs_fid_one.getInt("actual_time");
                              int result_capacity = rs_fid_one.getInt("capacity");
                              int result_price = rs_fid_one.getInt("price");
                              sb.append("ID: " + fid_one + " Day: " + dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum + 
                                        " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
                           
                           } catch (SQLException e) {
                              e.printStackTrace();
                           }
                           
                           // Find Flight values for fid_two and place in string buffer
                           
                           try {
                           
                              fidValuesStatement.clearParameters();
                              fidValuesStatement.setInt(1, fid_two);
                              ResultSet rs_fid_two = fidValuesStatement.executeQuery();
                              rs_fid_two.next();
                              String result_carrierId = rs_fid_two.getString("carrier_id");
                              String result_flightNum = rs_fid_two.getString("flight_num");
                              String result_originCity = rs_fid_two.getString("origin_city");
                              String result_destCity = rs_fid_two.getString("dest_city");
                              int result_time = rs_fid_two.getInt("actual_time");
                              int result_capacity = rs_fid_two.getInt("capacity");
                              int result_price = rs_fid_two.getInt("price");
                              sb.append("ID: " + fid_two + " Day: " + dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum + 
                                        " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
                           
                           } catch (SQLException e) {
                              e.printStackTrace();
                           } 
                            
                           // Insert itinerary data into the itinerary table
                           
                           int[] itineraries = {fid_one, fid_two};
                           MapItineraries.put(count, itineraries);
                           
                           // Update the counter
                           
                           count = count + 1;                    
                        
                        }
                        
                        rs_three.close();
                     
                     } catch (SQLException e) {
                     
                        e.printStackTrace();
                     
                     }
                  
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                  
                  }
               
               }
               
               // Find direct flights
               
               pullDirectStatement.clearParameters();
               pullDirectStatement.setInt(1, numberOfItineraries);
               ResultSet rs_three = pullDirectStatement.executeQuery();
               
               while (rs_three.next()) {
               
                  // Find itinerary information
                  
                  int fid_one = rs_three.getInt("fid_one");
                  int fid_two = rs_three.getInt("fid_two");
                  int total_time = rs_three.getInt("total_time");
                  int num_flights = rs_three.getInt("num_flights");
                  sb.append("Itinerary " + count + ": " + num_flights + " flight(s), " + total_time + " minutes\n");
                  
                  try {
                  
                     // Find flight information and save it into string buffer object
                     
                     fidValuesStatement.clearParameters();
                     fidValuesStatement.setInt(1, fid_one);
                     ResultSet rs_fid_one = fidValuesStatement.executeQuery();
                     rs_fid_one.next();
                     String result_carrierId = rs_fid_one.getString("carrier_id");
                     String result_flightNum = rs_fid_one.getString("flight_num");
                     String result_originCity = rs_fid_one.getString("origin_city");
                     String result_destCity = rs_fid_one.getString("dest_city");
                     int result_time = rs_fid_one.getInt("actual_time");
                     int result_capacity = rs_fid_one.getInt("capacity");
                     int result_price = rs_fid_one.getInt("price");
                     sb.append("ID: " + fid_one + " Day: " + dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum + 
                               " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
                  
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                  
                  }
                  
                  // Insert itinerary data into the itinerary table
                  
                  int[] itineraries = {fid_one, fid_two};
                  MapItineraries.put(count, itineraries);
                  
                  count = count + 1;
                  
               }
               
               rs_three.close();
               
               // Clear pre-itineraries table
               
               try {
         
                  clearItinerariesStatement.clearParameters();
                  clearItinerariesStatement.executeUpdate();
                  
               } catch (SQLException e) {
               
                  e.printStackTrace();
               
               }
               
               // Commit all previous queries and set autocommit to true 
               
               conn.commit();
               conn.setAutoCommit(true);
               
               // If the string buffer received no information, then return that no flights matched. Else, return the string containing all the flight information stored within the sb.
               
               if (sb.length() == 0) {
               
                  return "No flights match your selection\n";
               
               } else {
               
                  return sb.toString();
               
               }
               
          } catch (SQLException e) {
            
            e.printStackTrace();
          
          }
      
      } catch (SQLException e) {
      
         e.printStackTrace();
      }  
      
      try {
      
         conn.setAutoCommit(false);
         conn.rollback();
         conn.setAutoCommit(true);
      
      } catch (SQLException e) {
      
         e.printStackTrace();
      
      }      
      
      return "Failed to search\n";
    
    } finally {
           
      checkDanglingTransaction();
      
    }
    
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *         If the user is trying to book an itinerary with an invalid ID or without having done a
   *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   *         a reservation on the same day as the one that they are trying to book now, then return
   *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
   *         failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from 1 and
   *         increments by 1 each time a successful reservation is made by any user in the system.
   */
   public String transaction_book(int itineraryId) {
      
       try {
    
         // Check if user is logged in 
         
         if (!logged_in) {
         
            return "Cannot book reservations, not logged in\n";
         }
         
         // Iterate through the following transaction multiple times in order to solve deadlock error 
         // if multiple terminals/users booking actions coincide with one another
         
         for (int i = 0; i < 3; i++) {
         
            try {
            
               conn.setAutoCommit(false);
            
               // Check if there exists an itinerary within the itinerary tables with that specific ID
               
               if (!MapItineraries.containsKey(itineraryId)) {
                  
                  try {
                     
                     conn.rollback();
                     
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                     
                  }
                  
                  return "No such itinerary " + itineraryId + "\n";
                  
               }
                  
               // Get itinerary info 
                  
               int[] itineraries = MapItineraries.get(itineraryId);
               int fid_one = itineraries[0];
               int fid_two = itineraries[1];
                  
                     
                  try {
                     
                     checkFullStatement.clearParameters();
                     checkFullStatement.setInt(1, fid_one);
                     checkFullStatement.setInt(2, fid_one);
                     checkFullStatement.setInt(3, fid_one);
                     ResultSet checkFullResultSet = checkFullStatement.executeQuery();
                     checkFullResultSet.next();
                     int seats_left = checkFullResultSet.getInt("seats_left");
                        
                     if (seats_left <= 0) {
                     
                        try {
                     
                           conn.rollback();
                     
                        } catch (SQLException e) {
                  
                           e.printStackTrace();
                     
                        }

                        return "Booking failed\n";
                        
                     }
                     
                     checkFullResultSet.close();
                               
                     if (fid_two != -1) {
                        
                        try {
                              
                           checkFullStatement.clearParameters();
                           checkFullStatement.setInt(1, fid_two);
                           checkFullStatement.setInt(2, fid_two);
                           checkFullStatement.setInt(3, fid_two);
                           checkFullResultSet = checkFullStatement.executeQuery();
                           checkFullResultSet.next();
                           seats_left = checkFullResultSet.getInt("seats_left");
                              
                           if (seats_left <= 0) {
                           
                              try {
                                 
                                 conn.rollback();
                                 
                              } catch (SQLException e) {
                              
                                 e.printStackTrace();
                                 
                              }                              
                           
                              return "Booking failed \n";
                              
                           }
                           
                           checkFullResultSet.close();
                          
                        } catch (SQLException e) {
                        
                              e.printStackTrace();
                              
                        }
                           
                     }
           
                     // Checks if the user has already booked a flight on the same day
                        
                     try {
                        
                        countSameStatement.clearParameters();
                        countSameStatement.setInt(1, fid_one);
                        countSameStatement.setString(2, user_id);
                        ResultSet countSameResultSet = countSameStatement.executeQuery();
                        countSameResultSet.next();
                        int same_day = countSameResultSet.getInt("same_day");
                        
                        if (same_day != 0) {
                        
                           try {
                              
                              conn.rollback();
                              
                           } catch (SQLException e) {
                           
                              e.printStackTrace();
                              
                           }    
                                               
                           return "You cannot book two flights in the same day\n";
                           
                        }
                     
                        if (fid_two != -1) {
                           
                           try {
                           
                              countSameStatement.clearParameters();
                              countSameStatement.setInt(1, fid_two);
                              countSameStatement.setString(2, user_id);
                              countSameResultSet = countSameStatement.executeQuery();
                              countSameResultSet.next();
                              same_day = countSameResultSet.getInt("same_day");
                           
                              if (same_day != 0) {
                              
                                 try {
                     
                                    conn.rollback();
                     
                                 } catch (SQLException e) {
                  
                                    e.printStackTrace();
                     
                                 }
                              
                                 return "You cannot book two flights in the same day\n";
                                 
                              }
                           
                           } catch (SQLException e) {
                           
                              e.printStackTrace();
                              conn.rollback();
                              
                           }
                              
                        }
                           
                     // Finds cost of flight for fid_one and fid_two
                     
                     try {
                     
                        flightCostStatement.clearParameters();
                        flightCostStatement.setInt(1, fid_one);
                        ResultSet flightCostResultSet = flightCostStatement.executeQuery();
                        flightCostResultSet.next();
                        int fid_one_cost = flightCostResultSet.getInt("flight_cost");
                        int fid_two_cost = 0;
                        if (fid_two != -1) {
                        
                           try {
                           
                              flightCostStatement.clearParameters();
                              flightCostStatement.setInt(1, fid_two);
                              flightCostResultSet = flightCostStatement.executeQuery();
                              flightCostResultSet.next();
                              fid_two_cost = flightCostResultSet.getInt("flight_cost");  
                              
                           } catch (SQLException e) {
                           
                              e.printStackTrace();
                              conn.rollback();
                              
                           }                      
                        } 
                     
                        // Books flight for the user
                     
                        try {
                           
                           bookItineraryStatement.clearParameters();
                           bookItineraryStatement.setInt(1, fid_one);
                           bookItineraryStatement.setInt(2, fid_two);
                           bookItineraryStatement.setInt(3, fid_one_cost + fid_two_cost);
                           bookItineraryStatement.setString(4, user_id);
                           bookItineraryStatement.setInt(5, 0);
                           bookItineraryStatement.executeUpdate();
      
                           // Finds the max reservation id and returns it.
                           
                           try {
                              
                              findMaxStatement.clearParameters();
                              ResultSet findMaxResultSet = findMaxStatement.executeQuery();
                              
                              // Commit all the previous queries as a single transaction and set autocommit to true
                               
                              conn.commit();
                              conn.setAutoCommit(true);
                               
                              findMaxResultSet.next();
                              int re_id = findMaxResultSet.getInt("count_id");    
                                                                                        
                              return "Booked flight(s), reservation ID: " + re_id + "\n";
                             
                           } catch (SQLException e) {
                           
                              e.printStackTrace();
                              conn.rollback();
                              
                           }
                           
                        } catch (SQLException e) {
                        
                           e.printStackTrace();
                           conn.rollback();
                           
                        }  
                        
                     } catch (SQLException e) {
                     
                        e.printStackTrace();
                        conn.rollback();
                        
                     }
                  
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                     conn.rollback();
                     
                  }       
               
               } catch (SQLException e) {
               
                  e.printStackTrace();
                  conn.rollback();
                  
               } 
               
            } catch (SQLException e) {
            
               e.printStackTrace();
               
               try {
               
                  conn.rollback();
                  
               } catch (SQLException se) {
               
                  se.printStackTrace();
               
               }
            }
         }
         
         try {
         
            conn.setAutoCommit(false);
            conn.rollback();
            conn.setAutoCommit(true);
            
         } catch (SQLException e) {
         
            e.printStackTrace();
            
         }
         
         try {
            
            conn.rollback();
            
         } catch (SQLException e) {
         
            e.printStackTrace();
            
         }         
         
         return "Booking failed\n";
         
       } finally {
            
         checkDanglingTransaction();
         
       }
   }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *         is not found / not under the logged in user's name, then return "Cannot find unpaid
   *         reservation [reservationId] under user: [username]\n" If the user does not have enough
   *         money in their account, then return "User has only [balance] in account but itinerary
   *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
   *         [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
  
    try {
    
      // Checks if user is logged in
      
      if (!logged_in) {
      
         return "Cannot pay, not logged in\n";   
         
      }
      
      for (int i = 0; i < 3; i++) {
      
         // Set autocommit to false
         
         try {
         
            conn.setAutoCommit(false);
            
            // Checks if there exists a reservation under that ID and under the user's ID
            
            try {
               
               checkReservationStatement.clearParameters();
               checkReservationStatement.setString(1, user_id);
               checkReservationStatement.setInt(2, reservationId);
               ResultSet checkReservationResultSet = checkReservationStatement.executeQuery();
               checkReservationResultSet.next();
               int number_reservations = checkReservationResultSet.getInt("number_reservations");
               
               if (number_reservations != 1) {
               
                  try {
                  
                     conn.rollback();
                  
                  } catch (SQLException e) { 
                  
                     e.printStackTrace();
                  
                  }
               
                  return "Cannot find unpaid reservation " + reservationId + " under user: " + user_id +"\n";
                  
               }
            
               // Checks if the user has enough money to pay for that reservation
               
               try {
               
                 // Checks if the user can pay for the reservation
               
                  checkMoneyStatement.clearParameters();
                  checkMoneyStatement.setString(1, user_id);
                  checkMoneyStatement.setString(2, user_id);
                  checkMoneyStatement.setInt(3, reservationId);
                  ResultSet checkMoneyResultSet = checkMoneyStatement.executeQuery();
                  checkMoneyResultSet.next();
                  int balance_after = checkMoneyResultSet.getInt("balance_after");
                  int user_balance = checkMoneyResultSet.getInt("user_balance");
                  int total_price = checkMoneyResultSet.getInt("total_price");
                  
                  if (balance_after < 0) {
                  
                     try {
                     
                        conn.rollback();
                        
                        
                     } catch (SQLException e) {
                     
                        e.printStackTrace();
                     
                     }   
                  
                     return "User has only " + user_balance + " in account but itinerary costs " + total_price + "\n";
                     
                  }
        
                  // Pays for the reservation
                  
                  try {
                  
                     payReservationStatement.clearParameters();
                     payReservationStatement.setString(1, user_id);
                     payReservationStatement.setInt(2, reservationId);
                     payReservationStatement.executeUpdate(); 
                  
                     // If pay is successful, subtracts from the user's money the amount needed to pay for the reservation
                     
                     try {
                     
                        lessMoneyStatement.clearParameters();
                        lessMoneyStatement.setInt(1, balance_after);
                        lessMoneyStatement.setString(2, user_id);
                        lessMoneyStatement.executeUpdate();
                        
                        conn.commit();
                        conn.setAutoCommit(true);
                     
                        return "Paid reservation: " + reservationId + " remaining balance: " + balance_after + "\n";
                     
                     } catch (SQLException e) {
                     
                        if (i == 3) {
                        
                           e.printStackTrace();
                        
                        }
                        
                     }
                  
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                     
                  }
               
               } catch (SQLException e) {
               
                  e.printStackTrace();
                  
               }
            
            } catch (SQLException e) {
            
               e.printStackTrace();
               
            }
            
         } catch (SQLException e) {
         
            e.printStackTrace();
         
         }
      
      }
      
      try {
      
         conn.rollback();
         
      } catch (SQLException e) {
      
         e.printStackTrace();
      
      }
      
      return "Failed to pay for reservation " + reservationId + "\n";
      
    } finally {
    
      checkDanglingTransaction();
      
    }
    
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
  
    try {
    
      // Checks if user is logged in
      
      if (!logged_in) {
      
         return "Cannot view reservations, not logged in\n";
         
      }
      
      // Set autocommit to false
      
      try {
      
         conn.setAutoCommit(false);
      
      
         // Initialize a string buffer object to help store the itinerary return values
         
         StringBuffer sb = new StringBuffer();
         
         // Find number of reservations for the user 
         
         try {
            
            countReservationsStatement.clearParameters();
            countReservationsStatement.setString(1, user_id);
            ResultSet countReservationsResultSet = countReservationsStatement.executeQuery();
            countReservationsResultSet.next();
            int reservation_count = countReservationsResultSet.getInt("reservation_count");
            
            if (reservation_count == 0) {
            
               try {
               
                  conn.rollback();
               
               } catch (SQLException e) {
               
                  e.printStackTrace();
               
               }
            
               return "No reservations found\n";
               
            }
         
            // Gets all reservations for the user
     
            try {
               
               userReservationsStatement.clearParameters();
               userReservationsStatement.setString(1, user_id);
               ResultSet userReservationsResultSet = userReservationsStatement.executeQuery();
            
               while (userReservationsResultSet.next()) {
               
                  int re_id = userReservationsResultSet.getInt("re_id");
                  int fid_one = userReservationsResultSet.getInt("fid_one");
                  int fid_two = userReservationsResultSet.getInt("fid_two");
                  int pay_int = userReservationsResultSet.getInt("pay");
                  String pay_string = "";
                  
                  if (pay_int == 0) {
                  
                     pay_string = "false";
                     
                  }  else if (pay_int == 1) {
                  
                     pay_string = "true";
                     
                  }
                  
                  sb.append("Reservation " + re_id + " paid: " + pay_string + ":\n");
                  
                  try {
                  
                     // Find flight information and save it into string buffer object
                     
                     fidValuesStatement.clearParameters();
                     fidValuesStatement.setInt(1, fid_one);
                     ResultSet rs_fid_one = fidValuesStatement.executeQuery();
                     rs_fid_one.next();
                     String result_carrierId = rs_fid_one.getString("carrier_id");
                     String result_flightNum = rs_fid_one.getString("flight_num");
                     String result_originCity = rs_fid_one.getString("origin_city");
                     String result_destCity = rs_fid_one.getString("dest_city");
                     int result_time = rs_fid_one.getInt("actual_time");
                     int result_capacity = rs_fid_one.getInt("capacity");
                     int result_price = rs_fid_one.getInt("price");
                     int dayOfMonth = rs_fid_one.getInt("day_of_month");
                     sb.append("ID: " + fid_one + " Day: " + dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum + 
                               " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
                     
                     if (fid_two != -1) {
                     
                        try {     
                        
                           // Find flight information and save it into string buffer object
                           
                           fidValuesStatement.clearParameters();
                           fidValuesStatement.setInt(1, fid_two);
                           ResultSet rs_fid_two = fidValuesStatement.executeQuery();
                           rs_fid_two.next();
                           result_carrierId = rs_fid_two.getString("carrier_id");
                           result_flightNum = rs_fid_two.getString("flight_num");
                           result_originCity = rs_fid_two.getString("origin_city");
                           result_destCity = rs_fid_two.getString("dest_city");
                           result_time = rs_fid_two.getInt("actual_time");
                           result_capacity = rs_fid_two.getInt("capacity");
                           result_price = rs_fid_two.getInt("price");
                           dayOfMonth = rs_fid_two.getInt("day_of_month");
                           sb.append("ID: " + fid_two + " Day: " + dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum + 
                                     " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
                        
                        } catch (SQLException e) {
                        
                           e.printStackTrace();
                           
                        }               
                     }
                     
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                     
                  }
                  
               }
               
               conn.commit();
               conn.setAutoCommit(true);
               
               return sb.toString();
                 
            } catch (SQLException e) {
            
               e.printStackTrace();
               
            }
         
         } catch (SQLException e) {
         
            e.printStackTrace();
            
         }
      
      } catch (SQLException e) {
      
         e.printStackTrace();
      
      }
      
      try {
      
         conn.rollback();
      
      } catch (SQLException e) {
      
         e.printStackTrace();
      
      }

      return "Failed to retrieve reservations\n";
      
    } finally {
    
      checkDanglingTransaction();
      
    }
    
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *         all other errors, return "Failed to cancel reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
  
    try {
    
      // Checks if user is logged in
      
      if (!logged_in) {
      
         return "Cannot cancel reservations, not logged in\n";
         
      }
      
      for (int i = 0; i < 3; i++) {
      
         // Set autocommit to false
      
         try {
         
            conn.setAutoCommit(false);
         
            // Find out whether the user has a reservation with a particular ID
            
            try {
               
               specificReservationStatement.clearParameters();
               specificReservationStatement.setString(1, user_id);
               specificReservationStatement.setInt(2, reservationId);
               ResultSet specificReservationResultSet = specificReservationStatement.executeQuery();
               specificReservationResultSet.next();
               int count_reservations = specificReservationResultSet.getInt("count_reservations");
               
               if (count_reservations != 1) {
               
                  try {
                  
                     conn.rollback();
                  
                  } catch (SQLException e) {
                     
                     e.printStackTrace();
                     
                  }
               
                  return "Failed to cancel reservation " + reservationId +"\n";
                  
               }
               
               // Gets info regarding the reservation
               
               try {
               
                  reservationInfoStatement.clearParameters();
                  reservationInfoStatement.setString(1, user_id);
                  reservationInfoStatement.setString(2, user_id);
                  reservationInfoStatement.setInt(3, reservationId);
                  ResultSet reservationInfoResultSet = reservationInfoStatement.executeQuery();
                  reservationInfoResultSet.next();
                  int pay = reservationInfoResultSet.getInt("pay");
                  int total_price = reservationInfoResultSet.getInt("total_price");
                  int user_balance = reservationInfoResultSet.getInt("user_balance");
               
                  // Cancels the reservation
                  
                  try {
                     
                     cancelReservationStatement.clearParameters();
                     cancelReservationStatement.setString(1, user_id);
                     cancelReservationStatement.setInt(2, reservationId);
                     cancelReservationStatement.executeUpdate();
                     
                     // Adds the canceled reservation id into the Cancelled table
                     
                     try {
                     
                        addCancelledStatement.clearParameters();
                        addCancelledStatement.setInt(1, reservationId);
                        addCancelledStatement.executeUpdate();
        
                        // Refunds reservation if reservation was paid for
                        
                        if (pay == 1) {
                           
                           try {
                           
                              refundUserStatement.clearParameters();
                              refundUserStatement.setInt(1, user_balance + total_price);
                              refundUserStatement.setString(2, user_id);
                              refundUserStatement.executeUpdate();
                           
                           } catch (SQLException e) {
                           
                              e.printStackTrace();
                              
                           }
                            
                        }
                        
                        conn.commit();
                        conn.setAutoCommit(true);
                        
                        return "Canceled reservation " + reservationId + "\n";   
                        
                     } catch (SQLException e) {
                     
                        if (i == 3) {
                        
                           e.printStackTrace(); 
                           
                        }
                        
                     }
      
                  } catch (SQLException e) {
                  
                     e.printStackTrace();
                     
                  } 
               
               } catch (SQLException e) {
               
                  e.printStackTrace();
                  
               }
               
            } catch (SQLException e) {
            
               e.printStackTrace();
               
            }  
            
         } catch (SQLException e) {
            
            e.printStackTrace();
            
         }  
      
      }
      
      try {
      
         conn.rollback();
      
      } catch (SQLException e) {
      
         e.printStackTrace();
      
      }
      
      return "Failed to cancel reservation " + reservationId + "\n";
      
    } finally {
    
      checkDanglingTransaction();
      
    }
    
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   * 
   */
  private void checkDanglingTransaction() {
    try {
      try (ResultSet rs = tranCountStatement.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
              "Transaction not fully commit/rollback. Number of transaction in process: " + count);
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }

  private static boolean isDeadLock(SQLException ex) {
    return ex.getErrorCode() == 1205;
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
