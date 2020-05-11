import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PaymentManagement {
	
	/*
	 * Reconcile Methos alters ordes table using mapping like one order one payment and many orders one payment
	 */
	
	void reconcilePayments(Connection database) throws SQLException {
		
		
		//Making autocommint to false so that we can use savepoints in our logic
		database.setAutoCommit(false);
		
		//ordrers list store the order count
 
		List<Integer> orders=new ArrayList<Integer>();
		//payments contains all payment details group by customernumber
		HashMap<Integer,ArrayList<Float>> payments=new HashMap<Integer,ArrayList<Float>>();
		
		try {
			//making statement updatable so that resultset can be updated
			Statement statement=database.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
			        ResultSet.CONCUR_UPDATABLE);
			//statement.execute("use classicmodels;");
			ResultSet rs=statement.executeQuery("select orderNumber from orders;");
			while(rs.next()) {
				//stores all the orders into list
				orders.add(rs.getInt("orderNumber"));
			}
			
			for(int i=0;i<orders.size();i++) {
				//Below query adds the total billAmount which is calculated from orderdetails table
			statement.executeUpdate("update orders set billAmount=(select sum(quantityOrdered*priceEach) "
					+ "from orderdetails where orderNumber='"+orders.get(i)+"') \r\n" + 
					"where orderNumber='"+orders.get(i)+"';");
			}
			
			//below result set has all the payment details
			
			ResultSet rs1=statement.executeQuery("select customerNumber,amount from payments");
			while(rs1.next()) {
				
				//payments are loaded into hashmap as customerNumber as key and the payment amount as values;
				
				if(payments.containsKey(rs1.getInt("customerNumber"))) {
					
					List<Float> existingValueList=payments.get(rs1.getInt("customerNumber"));
					existingValueList.add(rs1.getFloat("amount"));
					
					payments.put(rs1.getInt("customerNumber"), (ArrayList<Float>) existingValueList);
					
				}
				else {
					List<Float> valueList=new ArrayList<Float>();
					valueList.add(rs1.getFloat("amount"));
					payments.put(rs1.getInt("customerNumber"), (ArrayList<Float>) valueList);
				}
				
			}
			
			for(Integer i:payments.keySet()) {
				
				//updates the scenario of one payment one order clause using payment hashmap
				
				List<Float> amountList=payments.get(i);
				
				for(Float f:amountList) {
				statement.executeUpdate("update orders set checkNumber= "
						+ "(select checkNumber from payments where customerNumber="+i+" and amount="+f+""
								+ ")\r\n" + 
						" where customerNumber="+i+" and billAmount="+f+"and orderDate <= \r\n" + 
								"                        (select paymentDate from payments where customerNumber='"+i+"' "
										+ "and amount='"+f+"');");
				}
				
			}
			
			
			/*
			 * Below code takes care of many to many mapping
			 * another statement is created so that we can update both the result sets and also to remove the error "result set closed"
			 */
			
			Statement statement1=database.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
			        ResultSet.CONCUR_UPDATABLE);
			

			ResultSet ordersMapping=null;
			//below query gets all the orders which has checkNumber as null after updating one to one scenario
			ResultSet paymentsMapping=statement.executeQuery("select payments.* from payments left join orders on orders.checkNumber=payments.checkNumber\r\n" + 
					"where orders.checkNumber is null order by payments.customerNumber,payments.paymentDate;");
			System.out.println("payments resultset"+paymentsMapping);
			while(paymentsMapping.next()) {
				float totalAmount=paymentsMapping.getFloat("amount");
				String checkNumber=paymentsMapping.getString("checkNumber");
				//below query gets all the order details for the orders for each hashmap pair
				String query="select orderNumber,orderDate,customerNumber,billAmount,checkNumber from orders \r\n" + 
						"where customerNumber='"+paymentsMapping.getInt("customerNumber")+"' and "
						+ "orderDate <= '"+paymentsMapping.getDate("paymentDate")+"' and checkNumber is null "
						+ "order by orderDate;";
				ordersMapping=statement1.executeQuery(query);
				float singleAmount=0;
				//Save point is created so that the updated checkNumbers can be rolled back incase of incorrect matching
				Savepoint savepoint = database.setSavepoint();			
				while(ordersMapping.next()) {
										
					singleAmount=singleAmount+ordersMapping.getFloat("billAmount");
					ordersMapping.updateString("checkNumber",checkNumber );
					ordersMapping.updateRow();
					if(singleAmount==totalAmount) {
						//loop breaks if the mapping occurs
						break;
					}
					else if(singleAmount>totalAmount) {
						
						//rolls back to save point if there us mismatch for mapping
						database.rollback(savepoint);	
					}										
					
				}
				
			}
			
			
			
		} catch (SQLException e) {
			
			System.out.println("SQL Error");
		}
		finally {
			//Commits the database at end
			database.commit();
		}
		
	}

	@SuppressWarnings("unused")
	boolean payOrder( Connection database, float amount, String cheque_number, ArrayList<Integer> orders ) {
		
		boolean flag=true;
		//Since set doesnt allow duplicates,all customerNumbers can be loaded into set because customer cant pay for other customer order
		Set<Integer> orderNumbers=new HashSet<Integer>();
		//DecimalFomrat is created so that the amount calculated from DB can get rounded off
		DecimalFormat df = new DecimalFormat("0.00");
		
		try {
			
			Statement statement=database.createStatement();
		  //statement.execute("use classicmodels;");
			Double billAmount=0.00;
			
			for(Integer i:orders) {
				
				//gets all the customer numbers and billAmount for given list of orders
				
				ResultSet eachBillAmount=statement.executeQuery("select billAmount,customerNumber from orders where orderNumber ='"+i+"';");
				
				if(eachBillAmount.next()) {
					orderNumbers.add(eachBillAmount.getInt("customerNumber"));
				billAmount=billAmount+eachBillAmount.getFloat("billAmount");
				df.format(billAmount);
				}
				
			}
			
			//if all the orders doesnt belong to one customer or if given amount doesnt match up to the total bill,false is returned
			
			if((Float.parseFloat(df.format(billAmount))!=amount)||orderNumbers.size()>1) {
				
				flag=false;
			}
			else {
				for(Integer i:orders) {		
					String sql="update orders set checkNumber='"+cheque_number+"' where orderNumber="+i+";";
					//Updates the checkNumber with given check number
					int updateCheckNumbers=statement.executeUpdate(sql);	
				}
				
			}
			
			database.commit();
			
		} catch (SQLException e) {
			System.out.println("SQL Error");

		}
		return flag;
	}
	List<String> unknownPayments(Connection database) {
		
		//String array list is loaded with all the unknown payments
		
		List<String> unknownCheckNumbers=new ArrayList<String>();
		try {
			Statement statement=database.createStatement();
			//statement.execute("use classicmodels;");
			String unknownCheckList="select payments.checkNumber from orders right join payments on orders.checkNumber=payments.checkNumber\r\n" + 
					"where orders.checkNumber is null;";
			ResultSet rs=statement.executeQuery(unknownCheckList);
			while(rs.next()) {
				unknownCheckNumbers.add(rs.getString("checkNumber"));
			}
			
		} catch (SQLException e) {
			System.out.println("SQL Error");
		}
		return unknownCheckNumbers;
	}
	List<Integer> unpaidOrder(Connection database) {
		
		//Integer array list is loaded with all the unpaired orders 
		List<Integer> unpaidOrdersList=new ArrayList<Integer>();		
		try {
			Statement statement=database.createStatement();
			
			String sqlForUnpaidList="SELECT orderNumber FROM orders "
					+ "where checkNumber is null and status not like 'Disputed' and status not like 'Cancelled';\r\n" + 
					"";
			ResultSet rs=statement.executeQuery(sqlForUnpaidList);
			while(rs.next()) {
				unpaidOrdersList.add(rs.getInt("orderNumber"));
			}
			//System.out.println(unpaidOrdersList);
			
		} catch (SQLException e) {
			System.out.println("SQL Error");
		}
		return unpaidOrdersList;
	}
	
}
