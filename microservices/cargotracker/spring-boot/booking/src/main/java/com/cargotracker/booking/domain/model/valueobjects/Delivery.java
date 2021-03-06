package com.cargotracker.booking.domain.model.valueobjects;

import com.cargotracker.booking.domain.model.entities.Location;

import javax.persistence.Embeddable;
import javax.persistence.Column;
import javax.persistence.Enumerated;
import javax.persistence.Embedded;
import javax.persistence.EnumType;

import java.util.Date;
import java.util.List;
import java.util.ListIterator;

/**
 * Domain class which tracks the progress of the Cargo against the Route Specification / Itinerary and Handling Events.
 */

@Embeddable
public class Delivery {

    public static final Date ETA_UNKOWN = null;

    //Enumerated Types - Routing Status / Transport Status of the Cargo
    @Enumerated(EnumType.STRING)
    @Column(name = "routing_status")
    private RoutingStatus routingStatus; //Routing Status of the Cargo
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_status")
    private TransportStatus transportStatus; //Transport Status of the Cargo

    //Current/PRevious information of the Cargo. Helps the operator in determining the current state is OK.
    @Column(name = "last_known_location_id")
    private Location lastKnownLocation;
    
    @Column(name = "current_voyage_id")
    private Voyage currentVoyage;
    
    @Embedded
    private LastCargoHandledEvent lastEvent;
    
    //Predictions for the Cargo activity. Helps the operator in determining if anything needs to be changed for the future
    public static final CargoHandlingActivity NO_ACTIVITY = new CargoHandlingActivity();
    
    @Embedded
    private CargoHandlingActivity nextExpectedActivity;


    public Delivery() {
        // Nothing to initialize
    }

    public Delivery(LastCargoHandledEvent lastEvent, CargoItinerary itinerary, RouteSpecification routeSpecification) {
        this.lastEvent = lastEvent;
        this.routingStatus = calculateRoutingStatus(itinerary, routeSpecification);
        
        this.transportStatus = calculateTransportStatus();
        this.lastKnownLocation = calculateLastKnownLocation();
        this.currentVoyage = calculateCurrentVoyage();
        this.nextExpectedActivity = calculateNextExpectedActivity(
                routeSpecification, itinerary);
    }

    /**
     * Creates a new delivery snapshot to reflect changes in routing, i.e. when
     * the route specification or the itinerary has changed but no additional
     * handling of the cargo has been performed.
     */
    public Delivery updateOnRouting(RouteSpecification routeSpecification, CargoItinerary itinerary) {
        return new Delivery(this.lastEvent, itinerary, routeSpecification);
    }

    /**
     *
     * @param routeSpecification
     * @param itinerary
     * @param handlingHistory
     * @return
     */
    public static Delivery derivedFrom(RouteSpecification routeSpecification, CargoItinerary itinerary, LastCargoHandledEvent lastCargoHandledEvent) {
        return new Delivery(lastCargoHandledEvent, itinerary, routeSpecification);
    }

    /**
     * Method to calculate the Routing status of a Cargo
     *
     * @param itinerary
     * @param routeSpecification
     * @return
     */
    private RoutingStatus calculateRoutingStatus(CargoItinerary itinerary, RouteSpecification routeSpecification) {
        if (itinerary == null || itinerary == CargoItinerary.EMPTY_ITINERARY) {
            return RoutingStatus.NOT_ROUTED;
        } else {
            return RoutingStatus.ROUTED;
        }
    }

    /**
     * Method to calculate the Transport Status of a Cargo
     * @return
     */
    private TransportStatus calculateTransportStatus() {
        if (lastEvent == null || lastEvent.getHandlingEventType() == null) {
            return TransportStatus.NOT_RECEIVED;
        }

        switch (lastEvent.getHandlingEventType()) {
            case "LOAD":
                return TransportStatus.ONBOARD_CARRIER;
            case "UNLOAD":
            case "RECEIVE":
            case "CUSTOMS":
                return TransportStatus.IN_PORT;
            case "CLAIM":
                return TransportStatus.CLAIMED;
            default:
                return TransportStatus.UNKNOWN;
        }
    }

    /**
     * Calculate Last known location
     * @return
     */
    private Location calculateLastKnownLocation() {
        if (lastEvent != null) {
            return new Location(lastEvent.getHandlingEventLocation());
        } else {
            return null;
        }
    }

    /**
     *
     * @return
     */
    private Voyage calculateCurrentVoyage() {
        if (getTransportStatus().equals(TransportStatus.ONBOARD_CARRIER) && lastEvent != null) {
            return new Voyage(lastEvent.getHandlingEventVoyage());
        } else {
            return null;
        }
    }

    private CargoHandlingActivity calculateNextExpectedActivity(RouteSpecification routeSpecification, CargoItinerary itinerary) {
    	/*if (currentVoyage != null) { System.out.println("CURRENT VOYAGE=" + currentVoyage.getVoyageId()); }
    	System.out.println("Route Specification 11=" + routeSpecification.toString());    	
    	System.out.println("LEGS : " + itinerary.toString());*/
    	
    	if (this.routingStatus == RoutingStatus.ROUTED && itinerary != null && currentVoyage != null) {
    		List<Leg> legs = itinerary.getLegs();
    		if (legs.isEmpty()) return NO_ACTIVITY;
    			
    		ListIterator<Leg> iterator = legs.listIterator();
    		while (iterator.hasNext()) {
    			Leg l = iterator.next();    		
    			if (currentVoyage.getVoyageId().equals(l.getVoyage().getVoyageId())) break;
    		}

    		Leg nextLeg = iterator.next();    		
    		String nextEventType = "UNKNOWN";
    		
    		if (lastEvent !=null) {    			
    			switch (lastEvent.getHandlingEventType()) {
            		case "LOAD":
            			nextEventType = "UNLOAD";
            			break;
            		case "UNLOAD":
            			if (nextLeg != null) nextEventType = "LOAD";
            			else nextEventType = "CUSTOMS";
            			
            			break;
            		default:
            			nextEventType = "UNKNOWN";
    			}
    		}

    		if (nextLeg !=null) {
    			System.out.println("HANDLIGNTYPE="+nextEventType);
    			System.out.println("VOYAGE="+nextLeg.getVoyage().getVoyageId());
    			CargoHandlingActivity nextActivity = new CargoHandlingActivity(nextEventType, nextLeg.getLoadLocation(), nextLeg.getVoyage());
    			System.out.println("nextActivity="+nextActivity.toString());
    			return nextActivity;
    		}
    	}
    	
    	return NO_ACTIVITY;
    }

    public RoutingStatus getRoutingStatus() { return this.routingStatus;}
    public TransportStatus getTransportStatus() { return this.transportStatus;}
    
    public Location getLastKnownLocation() {
        return this.lastKnownLocation;
    }
    
    public void setLastKnownLocation(Location lastKnownLocation) {
        this.lastKnownLocation = lastKnownLocation;
    }
    
    public void setLastEvent(LastCargoHandledEvent lastEvent) {
        this.lastEvent = lastEvent;
    }
    
    public Voyage getCurrentVoyage() {
        return this.currentVoyage;
    }

	public CargoHandlingActivity getNextExpectedActivity() {
		return nextExpectedActivity;
	}

	public void setNextExpectedActivity(CargoHandlingActivity nextExpectedActivity) {
		this.nextExpectedActivity = nextExpectedActivity;
	}

	public LastCargoHandledEvent getLastEvent() {
		return lastEvent;
	}

	public void setRoutingStatus(RoutingStatus routingStatus) {
		this.routingStatus = routingStatus;
	}

	public void setTransportStatus(TransportStatus transportStatus) {
		this.transportStatus = transportStatus;
	}

	public void setCurrentVoyage(Voyage currentVoyage) {
		this.currentVoyage = currentVoyage;
	}
}