package org.bukkit.event.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityEvent;

/**
 * Base class for vehicle-related events.
 */
public abstract class VehicleEvent extends EntityEvent {

    protected VehicleEvent() {
        this(null);
    }

    protected VehicleEvent(Vehicle vehicle) {
        super(vehicle);
    }

    /**
     * Returns the vehicle involved in this event.
     */
    public Vehicle getVehicle() {
        return (Vehicle) super.getEntity();
    }
}
