/* *********************************************************************** *
 * project: org.matsim.*
 * RouteTTObserver
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package telematics.common;

import org.apache.commons.math.stat.StatUtils;
import org.geotools.metadata.iso.extent.GeographicBoundingBoxImpl;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.events.algorithms.Vehicle2DriverEventHandler;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

public class RouteTTObserver implements PersonDepartureEventHandler, PersonArrivalEventHandler,
		LinkEnterEventHandler, IterationEndsListener, AfterMobsimListener, VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {

	private final int lastIteration;
	private Set<Id> route1;

	private Set<Id> route2;

	private Map<Id, Double> personTTs;

	private Map<Id, Double> departureTimes;

	private BufferedWriter writer;

	public double avr_route1TTs;

	public double avr_route2TTs;

	private double sumRoute1TTs;

	private double sumRoute2TTs;

	private String filename;
	
	Vehicle2DriverEventHandler vehicle2driver = new Vehicle2DriverEventHandler();
	private List<List<String>> iterationInfos = new ArrayList<>();

	@Inject
	RouteTTObserver(OutputDirectoryHierarchy controlerIO, EventsManager eventsManager, ControlerConfigGroup controlerCfg) {
		this.lastIteration = controlerCfg.getLastIteration();
		this.filename = controlerIO.getOutputFilename("routeTravelTimes.txt");
		eventsManager.addHandler(this);
		this.reset(0);
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		departureTimes.put(event.getPersonId(), event.getTime());
	}

	@Override
	public void reset(int iteration) {
		route1 = new HashSet<Id>();
		route2 = new HashSet<Id>();
		personTTs = new HashMap<Id, Double>();
		departureTimes = new HashMap<Id, Double>();
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		double depTime = departureTimes.get(event.getPersonId());
		if (depTime == 0)
			throw new RuntimeException("Agent departure time not found!");

		personTTs.put(event.getPersonId(), event.getTime() - depTime);
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (event.getLinkId().toString().equals("4")) {
			route1.add(vehicle2driver.getDriverOfVehicle(event.getVehicleId()));
		}
		else if (event.getLinkId().toString().equals("5")) {
			route2.add(vehicle2driver.getDriverOfVehicle(event.getVehicleId()));
		}
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {

			List<String> iterationInfo = new ArrayList<>();
			iterationInfo.add(String.valueOf(event.getIteration()));
			iterationInfo.add(String.valueOf(route1.size()));
			iterationInfo.add(String.valueOf(route2.size()));
			if (route1.isEmpty()) iterationInfo.add("0");
			else iterationInfo.add(String.valueOf(avr_route1TTs));
			if (route2.isEmpty()) iterationInfo.add("0");
			else iterationInfo.add(String.valueOf(avr_route2TTs));
			iterationInfo.add(String.valueOf(sumRoute1TTs));
			iterationInfo.add(String.valueOf(sumRoute2TTs));
			iterationInfo.add(String.valueOf(sumRoute1TTs + sumRoute2TTs));

		this.iterationInfos.add(iterationInfo);

		if(event.getIteration() == this.lastIteration){
			try {
				writer = org.matsim.core.utils.io.IOUtils.getBufferedWriter(filename);
				writer.write("it\tn_1\tn_2\ttt_avg_1\ttt_avg_2\ttt_sum_1\ttt_sum_2\ttt_sum");
				writer.newLine();

				for(int i= this.iterationInfos.size() - 1; i>=0; i-- ){
					List<String> line = iterationInfos.get(i);
					for(String e : line){
						writer.write(e + "\t");
					}
					writer.newLine();
				}

//				writer.write(String.valueOf(event.getIteration()));
//				writer.write("\t");
//				writer.write(String.valueOf(route1.size()));
//				writer.write("\t");
//				writer.write(String.valueOf(route2.size()));
//				writer.write("\t");
//
//				if (route1.isEmpty())
//					writer.write("0");
//				else
//					writer.write(String.valueOf(avr_route1TTs));
//				writer.write("\t");
//
//				if (route2.isEmpty())
//					writer.write("0");
//				else
//					writer.write(String.valueOf(avr_route2TTs));
//
//				writer.write("\t");
//				writer.write(String.valueOf(sumRoute1TTs));
//				writer.write("\t");
//				writer.write(String.valueOf(sumRoute2TTs));
//				writer.write("\t");
//				writer.write(String.valueOf(sumRoute1TTs + sumRoute2TTs));
//
//				writer.newLine();
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		List<Double> route1TTs = new ArrayList<Double>();
		List<Double> route2TTs = new ArrayList<Double>();

		for (Id p : route1) {
			route1TTs.add(personTTs.get(p));
		}
		for (Id p : route2) {
			route2TTs.add(personTTs.get(p));
		}
		if (route1TTs.isEmpty()){
			sumRoute1TTs = 0.0;
		}
		else {
			sumRoute1TTs = StatUtils.sum(toArray(route1TTs));
		}
		if (route2TTs.isEmpty()){
			sumRoute2TTs = 0.0;
		}
		else {
			sumRoute2TTs = StatUtils.sum(toArray(route2TTs));
		}

		avr_route1TTs = StatUtils.mean(toArray(route1TTs));
		avr_route2TTs = StatUtils.mean(toArray(route2TTs));

		if (Double.isNaN(avr_route1TTs)) {
            avr_route1TTs = getFreespeedTravelTime(event.getServices().getScenario().getNetwork().getLinks().get(Id.create("2", Link.class)));
            avr_route1TTs += getFreespeedTravelTime(event.getServices().getScenario().getNetwork().getLinks().get(Id.create("4", Link.class)));
            avr_route1TTs += getFreespeedTravelTime(event.getServices().getScenario().getNetwork().getLinks().get(Id.create("6", Link.class)));
		}
		if (Double.isNaN(avr_route2TTs)) {
            avr_route2TTs = getFreespeedTravelTime(event.getServices().getScenario().getNetwork().getLinks().get(Id.create("3", Link.class)));
            avr_route2TTs += getFreespeedTravelTime(event.getServices().getScenario().getNetwork().getLinks().get(Id.create("5", Link.class)));
            avr_route2TTs += getFreespeedTravelTime(event.getServices().getScenario().getNetwork().getLinks().get(Id.create("6", Link.class)));
		}
	}

	private double getFreespeedTravelTime(final Link link) {
		return link.getLength() / link.getFreespeed();
	}
	
	
	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		vehicle2driver.handleEvent(event);
	}


	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		vehicle2driver.handleEvent(event);
	}

	public static double[] toArray(List<Double> list){
		double[] a = new double[list.size()];
		for (int i = 0; i < list.size(); i++){
			a[i] = list.get(i);
		}
		return a;
	}

}