/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import java.util.*;

/**
 * A message that is created at a node or passed between nodes.
 */
public class Message implements Comparable<Message> {
	/** Value for infinite TTL of message */
	public static final int INFINITE_TTL = -1;
	private DTNHost from;
	private DTNHost to;
	/** Identifier of the message */
	private String id;
	/** Size of the message (bytes) */
	private int size;
	/** List of nodes this message has passed */
	private List<DTNHost> path;
	/** Next unique identifier to be given */
	private static int nextUniqueId;
	/** Unique ID of this message */
	private int uniqueId;
	/** The time this message was received */
	private double timeReceived;
	/** The time when this message was created */
	private double timeCreated;
	/** Initial TTL of the message */
	private int initTtl;

	/** if a response to this message is required, this is the size of the
	 * response message (or 0 if no response is requested) */
	private int responseSize;
	/** if this message is a response message, this is set to the request msg*/
	private Message requestMsg;

	/** Container for generic message properties. Note that all values
	 * stored in the properties should be immutable because only a shallow
	 * copy of the properties is made when replicating messages */
	private Map<String, Object> properties;

	/** Application ID of the application that created the message */
	private String	appID;

	static {
		reset();
		DTNSim.registerForReset(Message.class.getCanonicalName());
	}

	/**
	 * Creates a new Message.
	 * @param from Who the message is (originally) from
	 * @param to Who the message is (originally) to
	 * @param id Message identifier (must be unique for message but
	 * 	will be the same for all replicates of the message)
	 * @param size Size of the message (in bytes)
	 */
	public Message(DTNHost from, DTNHost to, String id, int size) {
		this.from = from;
		this.to = to;
		this.id = id;
		this.size = size;
		this.path = new ArrayList<DTNHost>();
		this.uniqueId = nextUniqueId;

		this.timeCreated = SimClock.getTime();
		this.timeReceived = this.timeCreated;
		this.initTtl = INFINITE_TTL;
		this.responseSize = 0;
		this.requestMsg = null;
		this.properties = null;
		this.appID = null;

		Message.nextUniqueId++;
		addNodeOnPath(from);
	}

	/**
	 * Returns the node this message is originally from
	 * @return the node this message is originally from
	 */
	public DTNHost getFrom() {
		return this.from;
	}

	/**
	 * Returns the node this message is originally to
	 * @return the node this message is originally to
	 */
	public DTNHost getTo() {
		return this.to;
	}

	/**
	 * Sets the recipient of this message.
	 * @param to The new recipient of the message
	 */
	public void setTo(DTNHost to) {
		this.to = to;
	}


	/**
	 * Returns the ID of the message
	 * @return The message id
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Returns an ID that is unique per message instance
	 * (different for replicates too)
	 * @return The unique id
	 */
	public int getUniqueId() {
		return this.uniqueId;
	}

	/**
	 * Returns the size of the message (in bytes)
	 * @return the size of the message
	 */
	public int getSize() {
		return this.size;
	}

	/**
	 * Adds a new node on the list of nodes this message has passed
	 * @param node The node to add
	 */
	public void addNodeOnPath(DTNHost node) {
		this.path.add(node);
	}

	/**
	 * Returns a list of nodes this message has passed so far
	 * @return The list as vector
	 */
	public List<DTNHost> getHops() {
		return this.path;
	}

	/**
	 * Returns the amount of hops this message has passed
	 * @return the amount of hops this message has passed
	 */
	public int getHopCount() {
		return this.path.size() -1;
	}

	/**
	 * Returns the time to live (minutes) of the message or Integer.MAX_VALUE
	 * if the TTL is infinite. Returned value can be negative if the TTL has
	 * passed already.
	 * @return The TTL (minutes)
	 */
	public int getTtl() {
		if (this.initTtl == INFINITE_TTL) {
			return Integer.MAX_VALUE;
		}
		else {
			return (int)( ((this.initTtl * 60) -
					(SimClock.getTime()-this.timeCreated)) /60.0 );
		}
	}


	/**
	 * Sets the initial TTL (time-to-live) for this message. The initial
	 * TTL is the TTL when the original message was created. The current TTL
	 * is calculated based on the time of
	 * @param ttl The time-to-live to set
	 */
	public void setTtl(int ttl) {
		this.initTtl = ttl;
	}

	/**
	 * Sets the time when this message was received.
	 * @param time The time to set
	 */
	public void setReceiveTime(double time) {
		this.timeReceived = time;
	}

	/**
	 * Returns the time when this message was received
	 * @return The time
	 */
	public double getReceiveTime() {
		return this.timeReceived;
	}

	/**
	 * Returns the time when this message was created
	 * @return the time when this message was created
	 */
	public double getCreationTime() {
		return this.timeCreated;
	}

	/**
	 * If this message is a response to a request, sets the request message
	 * @param request The request message
	 */
	public void setRequest(Message request) {
		this.requestMsg = request;
	}

	/**
	 * Returns the message this message is response to or null if this is not
	 * a response message
	 * @return the message this message is response to
	 */
	public Message getRequest() {
		return this.requestMsg;
	}

	/**
	 * Returns true if this message is a response message
	 * @return true if this message is a response message
	 */
	public boolean isResponse() {
		return this.requestMsg != null;
	}

	/**
	 * Sets the requested response message's size. If size == 0, no response
	 * is requested (default)
	 * @param size Size of the response message
	 */
	public void setResponseSize(int size) {
		this.responseSize = size;
	}

	/**
	 * Returns the size of the requested response message or 0 if no response
	 * is requested.
	 * @return the size of the requested response message
	 */
	public int getResponseSize() {
		return responseSize;
	}

	/**
	 * Returns a string representation of the message
	 * @return a string representation of the message
	 */
	public String toString () {
		return id;
	}

	/**
	 * Deep copies message data from other message. If new fields are
	 * introduced to this class, most likely they should be copied here too
	 * (unless done in constructor).
	 * @param m The message where the data is copied
	 */
	protected void copyFrom(Message m) {
		this.path = new ArrayList<DTNHost>(m.path);
		this.timeCreated = m.timeCreated;
		this.responseSize = m.responseSize;
		this.requestMsg  = m.requestMsg;
		this.initTtl = m.initTtl;
		this.appID = m.appID;

		if (m.properties != null) {
			Set<String> keys = m.properties.keySet();
			for (String key : keys) {
				updateProperty(key, m.getProperty(key));
			}
		}
	}

	/**
	 * Adds a generic property for this message. The key can be any string but
	 * it should be such that no other class accidently uses the same value.
	 * The value can be any object but it's good idea to store only immutable
	 * objects because when message is replicated, only a shallow copy of the
	 * properties is made.
	 * @param key The key which is used to lookup the value
	 * @param value The value to store
	 * @throws SimError if the message already has a value for the given key
	 */
	/**
	 * Adds a generic property for this message. The key can be any string but
	 * it should be such that no other class accidentally uses the same value.
	 * If the key already exists, the value is updated while preserving existing data.
	 *
	 * @param key The key which is used to lookup the value.
	 * @param value The value to store. If the key already exists, it will be merged.
	 */
	public void addProperty(String key, Object value) {
		if (this.properties == null) {
			this.properties = new HashMap<>();
		}

		if (this.properties.containsKey(key)) {
			Object existingValue = this.properties.get(key);

			if (existingValue instanceof List && value instanceof List) {
				// Gabungkan list lama dengan list baru
				((List<Object>) existingValue).addAll((List<Object>) value);
			} else if (existingValue instanceof Set && value instanceof Set) {
				// Gabungkan set lama dengan set baru
				((Set<Object>) existingValue).addAll((Set<Object>) value);
			} else if (existingValue instanceof Map && value instanceof Map) {
				// Gabungkan map lama dengan map baru
				((Map<Object, Object>) existingValue).putAll((Map<Object, Object>) value);
			} else {
				// Jika bukan koleksi, buat list dan simpan kedua nilai
				List<Object> newList = new ArrayList<>();
				newList.add(existingValue);
				newList.add(value);
				this.properties.put(key, newList);
			}
		} else {
			// Jika belum ada, langsung tambahkan
			this.properties.put(key, value);
		}
	}


	/**
	 * Returns an object that was stored to this message using the given
	 * key. If such object is not found, null is returned.
	 * @param key The key used to lookup the object
	 * @return The stored object or null if it isn't found
	 */
	public Object getProperty(String key) {
		if (this.properties == null) {
			return null;
		}
		return this.properties.get(key);
	}

	/**
	 * Updates a value for an existing property. For storing the value first
	 * time, {@link #addProperty(String, Object)} should be used which
	 * checks for name space clashes.
	 * @param key The key which is used to lookup the value
	 * @param value The new value to store
	 */
	public void updateProperty(String key, Object value) throws SimError {
		if (this.properties == null) {
			/* lazy creation to prevent performance overhead for classes
			   that don't use the property feature  */
			this.properties = new HashMap<String, Object>();
		}

		this.properties.put(key, value);
	}

	public void addSetProperty(String key, Object newValue) {
		if (this.properties == null) {
			this.properties = new HashMap<>();
		}

		Set<Object> values = (Set<Object>) this.properties.get(key);

		if (values == null) {// Inisialisasi hanya jika key belum ada
			this.properties.put(key, values);  // Simpan set yang baru
		}

		values.add(newValue);  // Tambahkan nilai baru ke set
		this.updateProperty(key, values);
	}




	/**
	 * Returns a replicate of this message (identical except for the unique id)
	 * @return A replicate of the message
	 */
	public Message replicate() {
		Message m = new Message(from, to, id, size);
		m.copyFrom(this);
		return m;
	}

	/**
	 * Compares two messages by their ID (alphabetically).
	 * @see String#compareTo(String)
	 */
	public int compareTo(Message m) {
		return toString().compareTo(m.toString());
	}

	/**
	 * Resets all static fields to default values
	 */
	public static void reset() {
		nextUniqueId = 0;
	}

	/**
	 * @return the appID
	 */
	public String getAppID() {
		return appID;
	}

	/**
	 * @param appID the appID to set
	 */
	public void setAppID(String appID) {
		this.appID = appID;
	}

}