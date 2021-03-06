package it.uniroma3.facade;

import it.uniroma3.model.*;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaQuery;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Stateless
public class OrderFacade {

	@PersistenceContext(unitName = "agave")
	private EntityManager em;

	public OrderFacade() {}

	/**
	 * Creates an order for a customer and with the given orderlines, the order is persisted
	 * 
	 * @param customer, order lines
	 * @return the created order
	 */
	public Orders createOrder(Customer customer, List<OrderLine> orderlines) {
		Orders order = new Orders();
		order.setCustomer(customer);
		order.setOrderLines(orderlines);

		Date creationTime = new Date();
		order.setCreationTime(creationTime);

		em.persist(order);
		return order;
	}

	/**
	 * Creates an order from a cart, updating the relation with the customer. The original cart is made empty
	 * 
	 * @param the cart
	 * @return the order
	 */
	public Orders createOrderFromCart(Orders cart) throws Exception {
		Exception e = new Exception();
		
		if (cart.getOrderLines().isEmpty())
			throw e;
		else {
		System.out.println("Retrieving Customer from Cart");
		Customer customer = em.createQuery("SELECT c FROM Customer c WHERE c.cart.id = " + cart.getId(), Customer.class).getSingleResult();

		System.out.println("Creating new Order");
		List<OrderLine> newOrderLines = createOrderLines(cart.getOrderLines());
		Orders order = createOrder(customer, newOrderLines);
		System.out.println("Order created");
		
		emptyCart(cart);
		
		return order;
		}
	}

	/**
	 * Gets an order from its id
	 * 
	 * @param the id
	 * @return the order
	 */
	public Orders getOrder(Long id) {
		Orders order = em.find(Orders.class, id);
		return order;
	}
	
	/**
	 * Gets an order line from its id
	 * 
	 * @param the id
	 * @return the order line
	 */
	public OrderLine getOrderLine(Long id){
		OrderLine orderLine = em.find(OrderLine.class, id);
		return orderLine;
	}

	/**
	 * Gets all the orders in the database
	 * 
	 * @param 
	 * @return the order list
	 */
	public List<Orders> getAllOrders() {
		CriteriaQuery<Orders> cq = em.getCriteriaBuilder().createQuery(Orders.class);
		cq.select(cq.from(Orders.class));
		List<Orders> ordersList = em.createQuery(cq).getResultList();
		return ordersList;
	}

	/**
	 * Adds a product to the cart, making an order line from the product and the quantity. The cart is then updated
	 * 
	 * @param cart, product, quantity
	 * @return 
	 */
	public void addProductToCart(Orders cart, Product product, Integer quantity) throws Exception{
		Orders c = getOrder(cart.getId());
		Exception e = new Exception();
		boolean check = true;

		if(c.containsProduct(product)){
			System.out.println("Product already exists");
			check = c.updateOrderLine(product, quantity);
		}
		else if(product.getStorageQuantity() >= quantity) {
			OrderLine ol = makeOrderLineFromProduct(product, quantity);
			c.addOrderLine(ol);
		}
		else check = false;

		if(!check) throw e;

		updateOrder(c);

		System.out.println("Cart Updated");
	}

	/**
	 * Makes an order line from a product and its quantity
	 * 
	 * @param product, quantity
	 * @return the order line
	 */
	public OrderLine makeOrderLineFromProduct(Product product, Integer quantity){
		OrderLine ol = new OrderLine(quantity, product);
		return ol;
	}

	public void removeOrderLine(Orders cart, OrderLine orderLine){
		Orders c = getOrder(cart.getId());
		OrderLine olToRemove = getOrderLine(orderLine.getId());

		c.removeOrderLine(olToRemove);

		deleteOrderLine(olToRemove);
		updateOrder(c);

		System.out.println("OrderLine Removed");
	}

	/**
	 * Makes a copy of a list of order lines
	 * 
	 * @param original order lines
	 * @return the copy
	 */
	public List<OrderLine> createOrderLines(List<OrderLine> orderLines){
		Iterator<OrderLine> olIterator = orderLines.iterator();
		List<OrderLine> newOrderLines = new LinkedList<OrderLine>();

		while(olIterator.hasNext()){
			OrderLine ol = olIterator.next();
			newOrderLines.add(makeOrderLineFromProduct(ol.getProduct(), ol.getQuantity()));
		}
		
		return newOrderLines;
	}
	
	/**
	 * All order lines are removed from the given order
	 * 
	 * @param cart
	 * @return 
	 */
	public void emptyCart(Orders cart){
		System.out.println("Emptying Cart");
		Orders c = getOrder(cart.getId());
		
		Iterator<OrderLine> olIterator = c.getOrderLines().iterator();

		while(olIterator.hasNext()){
			OrderLine ol = olIterator.next();
			deleteOrderLine(ol);
		}

		c.emptyOrderLines();
		updateOrder(c);
		System.out.println("Cart Empty");
	}
	
	
	/**
	 * Return the closed but not evaded order
	 * @return  the closed but not evaded order
	 */
	public List<Orders> getClosedOrders() {

		return 	em.createQuery("SELECT o "
			        	     + "FROM Orders o  "
				             + "WHERE o.closingDate <> NULL "
				             + "and o.evasionDate = NULL", Orders.class)
				             .getResultList();
	}

	/**
	 * Return the list of order lines in base of the
	 * corrispondence by the id of the product
	 * @param id
	 * @return
	 */
	public List<OrderLine> getOrderLines(Long id) {
		return getOrder(id).getOrderLines();
	}

	/**
	 * Return the last numOrders orders
	 * @param numOrders
	 * @return
	 */
	public List<Orders> getLastOrders(int numOrders) {

		List<Orders> orders = new ArrayList<Orders>();
		try { 
			orders = em.createQuery("SELECT o "
								  + "FROM Orders o "
								  + "ORDER BY o.id DESC"
								  , Orders.class).setMaxResults(numOrders).getResultList();
		}
		catch(Exception e){
			orders = null;
		}
		return orders;
	}
	
	/**
	 * Return all orders of a selected customer in base the correspondence of 
	 * the customer's id
	 * @param idCustomer
	 * @return
	 */
	public List<Orders> getAllOrdersFromCustomer(Long idCustomer){
		return em.createQuery("SELECT o "
				            + "FROM Orders o "
							+ "WHERE o.customer.id = :idCustomer", Orders.class)
							.setParameter("idCustomer", idCustomer)
							.getResultList();

	}

	/**
	 * Return all the closed orders of a selected customer in base the correspondence of 
	 * the customer's id
	 * @param idCustomer
	 * @return
	 */
	public List<Orders> getAllClosedOrdersFromCustomer(Long idCustomer) {
		return em.createQuery("SELECT o "
				            + "FROM Orders o "
							+ "WHERE o.customer.id = :idCustomer "
							+ "and o.evasionTime is NULL "
							+ "and o.creationTime is NOT NULL", Orders.class)
							.setParameter("idCustomer", idCustomer)
							.getResultList();
	}

	/**
	 * Return all evaded orders of a selected customer in base the correspondence of 
	 * the customer's id
	 * @param idCustomer
	 * @return
	 */
	public List<Orders> getAllEvadedOrdersFromCustomer(Long idCustomer) {
		return em.createQuery("SELECT o "
				            + "FROM Orders o "
							+ "WHERE o.customer.id = :idCustomer "
							+ "and o.evasionTime is not NULL", Orders.class)
							.setParameter("idCustomer", idCustomer)
							.getResultList();
	}

	/**
	 * Returns all closed order for all customer
	 * @return
	 */
	public List<Orders> getAllClosedOrders() {
		return em.createQuery("SELECT o "
				            + "FROM Orders o "
							+ "WHERE o.evasionTime is NULL "
							+ "and o.creationTime is NOT NULL", Orders.class)
							.getResultList();
	}
	

	public void updateOrder(Orders order) {
		em.merge(order);
	}	

	private void deleteOrder(Orders order) {
		em.remove(order);
	}

	public void deleteOrder(Long id) {
		Orders order = em.find(Orders.class, id);
		deleteOrder(order);
	}

	public void deleteOrderLine(OrderLine orderLine){
		em.remove(orderLine);
	}
	
}