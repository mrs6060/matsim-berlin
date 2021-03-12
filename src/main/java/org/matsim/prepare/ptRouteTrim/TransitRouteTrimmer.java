package org.matsim.prepare.ptRouteTrim;

import javafx.util.Pair;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This tool creates a trims TransitRoutes, so as not to enter a user-specified ESRI shape file.
 * There are several modifier methods that can be used separately or in combination.
 *
 * @author jakobrehmann
 */

//TODO Define Hub: if no hubReach=0, is it a hub?
//TODO: modes to use?
//TODO: Check Nullpointer exceptions in tests


public class TransitRouteTrimmer {
    private static final Logger log = Logger.getLogger(TransitRouteTrimmer.class);

    public static Pair<TransitSchedule, Vehicles> xxxDeleteRoutesEntirelyInsideZone(TransitSchedule transitScheduleOld, Vehicles vehiclesOld, Set<Id<TransitStopFacility>> stopsInZone, Set<Id<TransitLine>> linesToModify, Set<String> modes2Trim, boolean removeEmptyLines) {

        // make new TransitSchedule
        TransitSchedule transitScheduleNew = (new TransitScheduleFactoryImpl()).createTransitSchedule();
        for (TransitStopFacility stop : transitScheduleOld.getFacilities().values()) {
            transitScheduleNew.addStopFacility(stop);
        }

        // make new Vehicles as copy of old vehicles file
        Vehicles vehiclesNew = copyVehicles(vehiclesOld);
        Set<Id<Vehicle>> vehiclesAffectedByTrim = new HashSet<>();


        for (TransitLine line : transitScheduleOld.getTransitLines().values()) {
            if (!linesToModify.contains(line.getId())) {
                transitScheduleNew.addTransitLine(line);
                continue;
            }

            TransitLine lineNew = transitScheduleOld.getFactory().createTransitLine(line.getId());

            for (TransitRoute route : line.getRoutes().values()) {

                // Only handles specified routes.
                if (modes2Trim != null && !modes2Trim.isEmpty()) {
                    if (!modes2Trim.contains(route.getTransportMode())) {
                        lineNew.addRoute(route);
                        continue;
                    }
                }


                if (TransitRouteTrimmerUtils.pctOfStopsInZone(route, stopsInZone) == 1.0) {
                    vehiclesAffectedByTrim.addAll(route.getDepartures().values().stream().map(Departure::getVehicleId).collect(Collectors.toSet()));
                    continue;
                }


                lineNew.addRoute(route);

            }

            if (lineNew.getRoutes().size() == 0 && removeEmptyLines) {
                log.info(lineNew.getId() + " does not contain routes. It will NOT be added to the schedule");
                continue;
            }

            transitScheduleNew.addTransitLine(lineNew);

        }

        log.info("Old schedule contained " + transitScheduleOld.getTransitLines().values().size() + " lines.");
        log.info("New schedule contains " + transitScheduleNew.getTransitLines().values().size() + " lines.");

        Vehicles vehiclesNew2 = removeExtraVehicles(transitScheduleNew, vehiclesNew, vehiclesAffectedByTrim);

        return new Pair<>(transitScheduleNew, vehiclesNew2);
    }


    public static Pair<TransitSchedule, Vehicles> xxxTrimEnds(TransitSchedule transitScheduleOld, Vehicles vehiclesOld,
                                                              Set<Id<TransitStopFacility>> stopsInZone,
                                                              Set<Id<TransitLine>> linesToModify, boolean removeEmptyLines,
                                                              Set<String> modes2Trim, int minimumRouteLength,
                                                              boolean includeFirstStopWithinZone) {

        // make new TransitSchedule
        TransitSchedule transitScheduleNew = (new TransitScheduleFactoryImpl()).createTransitSchedule();
        for (TransitStopFacility stop : transitScheduleOld.getFacilities().values()) {
            transitScheduleNew.addStopFacility(stop);
        }

        Vehicles vehiclesNew = copyVehicles(vehiclesOld);

        for (TransitLine line : transitScheduleOld.getTransitLines().values()) {
            if (!linesToModify.contains(line.getId())) {
                transitScheduleNew.addTransitLine(line);
                continue;
            }

            TransitLine lineNew = transitScheduleOld.getFactory().createTransitLine(line.getId());

            for (TransitRoute route : line.getRoutes().values()) {
                TransitRoute routeNew = null;

                // Only handles specified routes.
                if (modes2Trim != null && !modes2Trim.isEmpty()) {

                    if (!modes2Trim.contains(route.getTransportMode())) {
                        lineNew.addRoute(route);
                        continue;
                    }
                }

                // Only handle routes that interact with zone
                if (TransitRouteTrimmerUtils.pctOfStopsInZone(route, stopsInZone) == 0.0) {
                    lineNew.addRoute(route);
                    continue;
                }

                routeNew = modifyRouteTrimEnds(route, minimumRouteLength, stopsInZone, includeFirstStopWithinZone, vehiclesNew);

                if (routeNew != null) {
                    lineNew.addRoute(routeNew);
                }

            }

            if (lineNew.getRoutes().size() == 0 && removeEmptyLines) {
                log.info(lineNew.getId() + " does not contain routes. It will NOT be added to the schedule");
                continue;
            }

            transitScheduleNew.addTransitLine(lineNew);

        }

        log.info("Old schedule contained " + transitScheduleOld.getTransitLines().values().size() + " lines.");
        log.info("New schedule contains " + transitScheduleNew.getTransitLines().values().size() + " lines.");

        TransitRouteTrimmerUtils.countLinesInOut(transitScheduleNew, stopsInZone);

//        Vehicles vehiclesNew = removeExtraVehicles(transitScheduleNew, vehiclesOld, vehiclesAffectedByTrim);

        return new Pair<>(transitScheduleNew, vehiclesNew);

    }

    public static Pair<TransitSchedule, Vehicles> xxxSkipStops(TransitSchedule transitScheduleOld, Vehicles vehiclesOld,
                                                               Set<Id<TransitStopFacility>> stopsInZone,
                                                               Set<Id<TransitLine>> linesToModify, boolean removeEmptyLines,
                                                               Set<String> modes2Trim,
                                                               int minimumRouteLength,
                                                               boolean includeFirstStopWithinZone) {

        // make new TransitSchedule
        TransitSchedule transitScheduleNew = (new TransitScheduleFactoryImpl()).createTransitSchedule();
        for (TransitStopFacility stop : transitScheduleOld.getFacilities().values()) {
            transitScheduleNew.addStopFacility(stop);
        }

        Vehicles vehiclesNew = copyVehicles(vehiclesOld);

//        Set<Id<Vehicle>> vehiclesAffectedByTrim = new HashSet<>();

        for (TransitLine line : transitScheduleOld.getTransitLines().values()) {
            if (!linesToModify.contains(line.getId())) {
                transitScheduleNew.addTransitLine(line);
                continue;
            }

            TransitLine lineNew = transitScheduleOld.getFactory().createTransitLine(line.getId());

            for (TransitRoute route : line.getRoutes().values()) {
                TransitRoute routeNew = null;

                // Only handles specified routes.
                if (modes2Trim != null && !modes2Trim.isEmpty()) {
                    if (!modes2Trim.contains(route.getTransportMode())) {
                        lineNew.addRoute(route);
                        continue;
                    }
                }

                // Only handle routes that interact with zone
                if (TransitRouteTrimmerUtils.pctOfStopsInZone(route, stopsInZone) == 0.0) {
                    lineNew.addRoute(route);
                    continue;
                }

                routeNew = modifyRouteSkipStopsWithinZone(route, minimumRouteLength, stopsInZone, includeFirstStopWithinZone, vehiclesNew);

                if (routeNew != null) {
                    lineNew.addRoute(routeNew);
                }
            }

            if (lineNew.getRoutes().size() == 0 && removeEmptyLines) {
                log.info(lineNew.getId() + " does not contain routes. It will NOT be added to the schedule");
                continue;
            }

            transitScheduleNew.addTransitLine(lineNew);

        }

        log.info("Old schedule contained " + transitScheduleOld.getTransitLines().values().size() + " lines.");
        log.info("New schedule contains " + transitScheduleNew.getTransitLines().values().size() + " lines.");

//        Vehicles vehiclesNew = removeExtraVehicles(transitScheduleNew, vehiclesOld, vehiclesAffectedByTrim);
        return new Pair<>(transitScheduleNew, vehiclesNew);

    }

    public static Pair<TransitSchedule, Vehicles> xxxSplitRoute(TransitSchedule transitScheduleOld, Vehicles vehiclesOld,
                                                                Set<Id<TransitStopFacility>> stopsInZone,
                                                                Set<Id<TransitLine>> linesToModify, boolean removeEmptyLines, Set<String> modes2Trim, int minimumRouteLength,
                                                                boolean includeFirstStopWithinZone,
                                                                boolean allowHubsWithinZone,
                                                                boolean includeFirstHubInZone,
                                                                int allowableStopsWithinZone) {

        // make new TransitSchedule
        TransitSchedule transitScheduleNew = (new TransitScheduleFactoryImpl()).createTransitSchedule();
        for (TransitStopFacility stop : transitScheduleOld.getFacilities().values()) {
            transitScheduleNew.addStopFacility(stop);
        }

        Vehicles vehiclesNew = copyVehicles(vehiclesOld);


        for (TransitLine line : transitScheduleOld.getTransitLines().values()) {
            if (!linesToModify.contains(line.getId())) {
                transitScheduleNew.addTransitLine(line);
                continue;
            }

            TransitLine lineNew = transitScheduleOld.getFactory().createTransitLine(line.getId());

            for (TransitRoute route : line.getRoutes().values()) {

                // Only handles specified routes.
                if (modes2Trim!=null && !modes2Trim.isEmpty()) {
                    if (!modes2Trim.contains(route.getTransportMode())) {
                        lineNew.addRoute(route);
                        continue;
                    }
                }

                // Only handle routes that interact with zone
                if (TransitRouteTrimmerUtils.pctOfStopsInZone(route, stopsInZone) == 0.0) {
                    lineNew.addRoute(route);
                    continue;
                }

                ArrayList<TransitRoute> routesNew = modifyRouteSplitRoute(route, stopsInZone, includeFirstStopWithinZone, allowHubsWithinZone,
                        includeFirstHubInZone, allowableStopsWithinZone, vehiclesNew);


                for (TransitRoute rt : routesNew) {

                    int routeLength = rt.getStops().size(); // TODO:check

                    if (routeLength >= minimumRouteLength && routeLength > 0) {
                        lineNew.addRoute(rt);
                    }
                }

            }

            if (lineNew.getRoutes().size() == 0 && removeEmptyLines) {
                log.info(lineNew.getId() + " does not contain routes. It will NOT be added to the schedule");
                continue;
            }

            transitScheduleNew.addTransitLine(lineNew);

        }

        log.info("Old schedule contained " + transitScheduleOld.getTransitLines().values().size() + " lines.");
        log.info("New schedule contains " + transitScheduleNew.getTransitLines().values().size() + " lines.");

        TransitRouteTrimmerUtils.countLinesInOut(transitScheduleNew, stopsInZone);


//        Vehicles vehiclesNew = removeExtraVehicles(transitScheduleNew, vehiclesOld, vehiclesAffectedByTrim);

        return new Pair<>(transitScheduleNew, vehiclesNew);

    }

    // This will skip stops within zone. If beginning or end of route is within zone, it will cut those ends off.
    private static TransitRoute modifyRouteSkipStopsWithinZone(TransitRoute routeOld, int minimumRouteLength,
                                                               Set<Id<TransitStopFacility>> stopsInZone, boolean includeFirstStopWithinZone,
                                                               Vehicles vehiclesNew) {
        List<TransitRouteStop> stops2Keep = new ArrayList<>();
        List<TransitRouteStop> stopsOld = new ArrayList<>(routeOld.getStops());

        for (int i = 0; i < stopsOld.size(); i++) {
            TransitRouteStop stop = stopsOld.get(i);
            // If stop is outside of zone, keep it
            if (!stopsInZone.contains(stop.getStopFacility().getId())) {
                stops2Keep.add(stop);
                continue;
            }

            // If stop is inside zone, but the stop before or after it is outside, then keep it
            if (includeFirstStopWithinZone) {
                // Checks if previous stop is outside of zone; if yes, include current stop
                if (i > 0) {
                    Id<TransitStopFacility> prevStop = stopsOld.get(i - 1).getStopFacility().getId();
                    if (!stopsInZone.contains(prevStop)) {
                        stops2Keep.add(stop); // TODO ADD ATTRIBUTE
                        continue;
                    }
                }

                // Checks if next stop is outside of zone; if yes, include current stop
                if (i < stopsOld.size() - 1) {
                    Id<TransitStopFacility> nextStop = stopsOld.get(i + 1).getStopFacility().getId();
                    if (!stopsInZone.contains(nextStop)) {
                        stops2Keep.add(stop);
                    }
                }
            }
        }


        if (stops2Keep.size() >= minimumRouteLength && stops2Keep.size() > 0) {
            return createNewRoute(routeOld, stops2Keep, 1, vehiclesNew);
        }

        return null; // TODO: Check this

    }

    private static TransitRoute modifyRouteTrimEnds(TransitRoute routeOld,
                                                    int minimumRouteLength,
                                                    Set<Id<TransitStopFacility>> stopsInZone,
                                                    boolean includeFirstStopWithinZone,
                                                    Vehicles vehiclesNew) {

        List<TransitRouteStop> stops2Keep = new ArrayList<>();
        List<TransitRouteStop> stopsOld = new ArrayList<>(routeOld.getStops());


        // cut stops from beginning of route, if they are within zone
        Id<TransitStopFacility> startStopId = null; //TODO: ???
        for (int i = 0; i < stopsOld.size(); i++) {

            Id<TransitStopFacility> id = stopsOld.get(i).getStopFacility().getId();
            if (!stopsInZone.contains(id)) {
                if (includeFirstStopWithinZone && i > 0) {
                    startStopId = stopsOld.get(i - 1).getStopFacility().getId();
                } else {
                    startStopId = id;
                }

                break;
            }

        }

        // cut stops from end of route, if they are within zone
        Id<TransitStopFacility> lastStopId = null;//TODO: ???
        for (int i = stopsOld.size() - 1; i >= 0; i--) {

            Id<TransitStopFacility> id = stopsOld.get(i).getStopFacility().getId();
            if (!stopsInZone.contains(id)) {
                if (includeFirstStopWithinZone && i < stopsOld.size() - 1) {
                    lastStopId = stopsOld.get(i + 1).getStopFacility().getId();
                } else {
                    lastStopId = id;
                }
                break;
            }
        }

        if (startStopId == null || lastStopId == null) {
            return null;
        }

        boolean start = false;
        for (TransitRouteStop stop : stopsOld) {
            if (!start) {
                if (stop.getStopFacility().getId().equals(startStopId)) {
                    stops2Keep.add(stop);
                    start = true;
                }
                continue;
            }

            if (stop.getStopFacility().getId().equals(lastStopId)) {
                stops2Keep.add(stop);
                break;
            }
            stops2Keep.add(stop);

        }

        if (stops2Keep.size() >= minimumRouteLength && stops2Keep.size() > 0) {
            return createNewRoute(routeOld, stops2Keep, 1, vehiclesNew);
        }

        return null; // TODO: Check this

    }

    private static ArrayList<TransitRoute> modifyRouteSplitRoute(TransitRoute routeOld,
                                                                 Set<Id<TransitStopFacility>> stopsInZone, boolean includeFirstStopWithinZone,
                                                                 boolean allowHubsWithinZone, boolean includeFirstHubInZone, int allowableStopsWithinZone,
                                                                 Vehicles vehiclesNew) {

        ArrayList<TransitRoute> resultRoutes = new ArrayList<>();
        List<TransitRouteStop> stopsOld = new ArrayList<>(routeOld.getStops());

        // Get list of hubs: each hub is represented [location, reach] where location is the index along the route and
        // reach is the number of stops away the hub can be from the edge of the zone to still be included.
        List<int[]> hubLocationAndReach = getHubList(stopsOld);

        // check if stop is within or outside of zone, and store in boolean array
        boolean[] stops2keep = new boolean[stopsOld.size()];
        for (int i = 0; i < stopsOld.size(); i++) {
            stops2keep[i] = (!stopsInZone.contains(stopsOld.get(i).getStopFacility().getId()));
        }


        // Exact start and end indices from boolean array.
        List<Integer[]> routeIndices = findStartEndIndicesForAllRoutes(stops2keep);

        // Extend routes with hubs and/or first stop within zone
        for (Integer[] pair : routeIndices) {
            int leftIndex = pair[0];
            int leftIndexNew = leftIndex;

            int rightIndex = pair[1];
            int rightIndexNew = rightIndex;


            // Add hubs
            if (allowHubsWithinZone && !hubLocationAndReach.isEmpty()) {
                List<Integer> hubPositionsLeft = new ArrayList<>();
                List<Integer> hubPositionsRight = new ArrayList<>();

                for (int[] hubPosValuePair : hubLocationAndReach) {
                    int hubPos = hubPosValuePair[0];
                    int hubReach = hubPosValuePair[1];

                    // add hub before beginning of route
                    if (hubPos < leftIndex) {
                        hubPositionsLeft.add(hubPos);
                        if (hubPos < leftIndexNew && hubPos + hubReach >= leftIndex) {
                            leftIndexNew = hubPos;
                        }
                    }
                    // add hub after end of route
                    if (hubPos > rightIndex) {
                        hubPositionsRight.add(hubPos);
                        if (hubPos > rightIndexNew && hubPos - hubReach <= rightIndex) {
                            rightIndexNew = hubPos;
                        }
                    }
                }


                // if there are hubs in a particular direction, but none are included in route because their reaches
                // aren't large enough, then we can add the closest hub afterward.
                if (includeFirstHubInZone) {
                    if (leftIndex == leftIndexNew && !hubPositionsLeft.isEmpty()) {
                        leftIndexNew = Collections.max(hubPositionsLeft);
                    }

                    if (rightIndex == rightIndexNew && !hubPositionsRight.isEmpty()) {
                        rightIndexNew = Collections.min(hubPositionsRight);
                    }
                }

            }

            // add first stop within zone if hub hasn't already extended the route in the respective direction
            if (includeFirstStopWithinZone) {
                if (leftIndex == leftIndexNew) {
                    if (leftIndex > 0) {
                        leftIndexNew--;
                    }
                }

                if (rightIndex == rightIndexNew) {
                    if (rightIndex < stopsOld.size() - 1) {
                        rightIndexNew++;
                    }
                }
            }

            pair[0] = leftIndexNew;
            pair[1] = rightIndexNew;
        }


        // combine routes if they overlap
        boolean[] stops2keep2 = new boolean[stopsOld.size()];
        for (Integer[] pair : routeIndices) {
            for (int i = pair[0]; i <= pair[1]; i++) {
                stops2keep2[i] = true;
            }
        }

        // fill gaps of two or less
        int zeroCnt = 0;
        List<Integer> tmpIndex = new ArrayList<>();
        for (int i = 0; i < stops2keep2.length; i++) {
            if (!stops2keep2[i]) {
                zeroCnt++;
                tmpIndex.add(i);
            } else {
                if (zeroCnt > 0 && zeroCnt <= allowableStopsWithinZone) {
                    for (int index : tmpIndex) {
                        stops2keep2[index] = true;
                    }
                }
                zeroCnt = 0;
                tmpIndex.clear();
            }
        }

        List<Integer[]> routeIndices2 = findStartEndIndicesForAllRoutes(stops2keep2);

        // create transit routes
        int newRouteCnt = 1;
        for (Integer[] pair : routeIndices2) {
            resultRoutes.add(createNewRoute(routeOld, pair[0], pair[1], newRouteCnt, vehiclesNew));
            newRouteCnt++;
        }

        return resultRoutes;

    }

    private static Vehicles copyVehicles(Vehicles vehiclesOld) {
        Vehicles vehiclesNew = VehicleUtils.createVehiclesContainer();
        for (VehicleType vehicleType : vehiclesOld.getVehicleTypes().values()) {
            vehiclesNew.addVehicleType(vehicleType);
        }

        for (Vehicle vehicle : vehiclesOld.getVehicles().values()) {
            vehiclesNew.addVehicle(vehicle);
        }

        return vehiclesNew;
    }

    private static Vehicles removeExtraVehicles(TransitSchedule transitScheduleNew, Vehicles vehicles, Set<Id<Vehicle>> vehiclesAffectedByTrim) {
        // Delete vehicles affected by trim that are not used by other lines
        Set<Id<Vehicle>> vehiclesUsedInTransitSchedule = transitScheduleNew.getTransitLines().values().stream()
                .flatMap(x -> x.getRoutes().values().stream()
                        .flatMap(y -> y.getDepartures().values().stream()
                                .map(Departure::getVehicleId))).collect(Collectors.toSet());

        int vehiclesRemoved = 0;
        for (Id<Vehicle> vehId : vehiclesAffectedByTrim) {
            if (!vehiclesUsedInTransitSchedule.contains(vehId)) {
                vehicles.removeVehicle(vehId);
                vehiclesRemoved++;
            }
        }
        log.info(vehiclesRemoved + " vehicles removed");

        return vehicles;
    }

    private static List<int[]> getHubList(List<TransitRouteStop> stopsOld) {
        List<int[]> hubs = new ArrayList<>();

        for (int i = 0; i < stopsOld.size(); i++) {
            TransitRouteStop stop = stopsOld.get(i);
            if (stop.getStopFacility().getAttributes().getAsMap().containsKey("hub-reach")) {
                int hubValue = (int) stop.getStopFacility().getAttributes().getAttribute("hub-reach");
                if (hubValue > 0) {
                    int[] hubPosValuePair = {i, hubValue};
                    hubs.add(hubPosValuePair);
                }
            }
        }
        return hubs;
    }

    private static List<Integer[]> findStartEndIndicesForAllRoutes(boolean[] stops2Keep) {
        List<Integer[]> routeIndicies = new ArrayList<>();
        Integer startIndex = null;
        Integer endIndex = null;

        for (int i = 0; i < stops2Keep.length; i++) {
            // we only look at stops that are outside of the zone (which should be kept)
            if (stops2Keep[i]) {
                // if the route has not begun previously, then begin it at the current index
                if (startIndex == null) {
                    startIndex = i;
                }
                // If this is the last stop, end route at this stop
                if (i == stops2Keep.length - 1) {
                    endIndex = i;
                }
                // If this is not last stop, and next stop is within zone, end route at this stop
                else {
                    if (!stops2Keep[i + 1]) {

                        endIndex = i;
                    }
                }
            }
            // the route is complete when we have a start and end index --> add route indicies to routeIndicies
            if (startIndex != null && endIndex != null) {
                routeIndicies.add(new Integer[]{startIndex, endIndex});
                startIndex = null;
                endIndex = null;
            }
        }

        return routeIndicies;
    }


    private static TransitRoute createNewRoute(TransitRoute routeOld, Integer startIndex, Integer endIndex, int modNumber, Vehicles vehiclesNew) {
        List<TransitRouteStop> stopsInNewRoute = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            stopsInNewRoute.add(routeOld.getStops().get(i));
        }

        return createNewRoute(routeOld, stopsInNewRoute, modNumber, vehiclesNew);
    }

    private static TransitRoute createNewRoute(TransitRoute routeOld, List<TransitRouteStop> stopsInNewRoute,
                                               int modNumber, Vehicles vehiclesNew) {

        TransitRoute routeNew;
        TransitScheduleFactory tsf = new TransitScheduleFactoryImpl();

        List<Id<Link>> linksOld = new ArrayList<>();
        linksOld.add(routeOld.getRoute().getStartLinkId());
        linksOld.addAll(routeOld.getRoute().getLinkIds());
        linksOld.add(routeOld.getRoute().getEndLinkId());

        Id<Link> startLinkNew = stopsInNewRoute.get(0).getStopFacility().getLinkId();
        Id<Link> endLinkNew = stopsInNewRoute.get(stopsInNewRoute.size() - 1).getStopFacility().getLinkId();
        ArrayList<Id<Link>> midLinksNew = new ArrayList<>();

        Iterator<TransitRouteStop> stopIt = stopsInNewRoute.iterator();

        boolean start = false;
        TransitRouteStop stop = stopIt.next();
        for (Id<Link> linkId : linksOld) {
            if (!start) {
                if (linkId.equals(startLinkNew)) {
                    start = true;
                    stop = stopIt.next();
                }
                continue;
            }

            if (!stopIt.hasNext() && linkId.equals(endLinkNew)) {

                break;
            }

            midLinksNew.add(linkId);

            if (linkId.equals(stop.getStopFacility().getLinkId())) {

                stop = stopIt.next();

            }

        }

        // Modify arrivalOffset and departureOffset for each stop
//        TransitRouteStop transitRouteStop = routeOld.getStops().get(0);
//
//
//        double initialDepartureOffset = transitRouteStop.getDepartureOffset().seconds();
//        double departureOffset = stopsInNewRoute.get(0).getDepartureOffset().seconds() - initialDepartureOffset;
//
//         double initialArrivalOffset = transitRouteStop.getArrivalOffset().seconds();
//        double arrivalOffset = departureOffset; //TODO: OFFSETS
        // double arrivalOffset = stopsInNewRoute.get(0).getArrivalOffset().seconds() - initialArrivalOffset;

//        List<TransitRouteStop> stopsNew = new ArrayList<>(copyStops(stopsInNewRoute, departureOffset, arrivalOffset));


        // make route.
        NetworkRoute networkRouteNew = RouteUtils.createLinkNetworkRouteImpl(startLinkNew, midLinksNew, endLinkNew);
        String routeIdOld = routeOld.getId().toString();
        Id<TransitRoute> routeIdNew = Id.create(routeIdOld + "_mod" + modNumber, TransitRoute.class);
        routeNew = tsf.createTransitRoute(routeIdNew, networkRouteNew, stopsInNewRoute, routeOld.getTransportMode());
        routeNew.setDescription(routeOld.getDescription());

        VehiclesFactory vf = vehiclesNew.getFactory();

        for (Departure departure : routeOld.getDepartures().values()) {
            Id<Vehicle> vehIdOld = departure.getVehicleId();
            Id<Vehicle> vehIdNew = Id.createVehicleId(vehIdOld.toString() + "_mod" + modNumber);
            VehicleType vehType = vehiclesNew.getVehicles().get(vehIdOld).getType();
            // this.vehicles.removeVehicle(departure.getVehicleId()); //TODO VEH MUST BE REMOVED LATER ON IF UNUSED
            Vehicle vehicle = vf.createVehicle(vehIdNew, vehType);
            vehiclesNew.addVehicle(vehicle);

            String depIdOld = departure.getId().toString();
            Departure departureNew = tsf.createDeparture(Id.create(depIdOld + "_mod" + modNumber, Departure.class), departure.getDepartureTime() );
            departureNew.setVehicleId(vehIdNew);
            routeNew.addDeparture(departureNew);
        }

        return routeNew;

    }


    // TODO: how to deal with arrival and departure offsets? I don't know the conventions...
    // TODO: first stop can have no arr offset, last stop no dep offset
    private static Collection<? extends TransitRouteStop> copyStops(List<TransitRouteStop> s, Double
            departureOffset, Double arrivalOffset) {
        List<TransitRouteStop> stops = new ArrayList<>();
        TransitRouteStop newStop;

        TransitScheduleFactory tsf = new TransitScheduleFactoryImpl();

        for (TransitRouteStop oldStop : s) {

            //            newStop = tsf.createTransitRouteStop(oldStop.getStopFacility(),
            //                    oldStop.getDepartureOffset().seconds() - departureOffset,
            //                    oldStop.getDepartureOffset().seconds() - departureOffset);
            if (oldStop.getDepartureOffset().isDefined() && oldStop.getArrivalOffset().isDefined()) {
                newStop = tsf.createTransitRouteStop(oldStop.getStopFacility(),
                        oldStop.getDepartureOffset().seconds() - departureOffset,
                        oldStop.getArrivalOffset().seconds() - arrivalOffset);
            } else if (oldStop.getDepartureOffset().isDefined()) {
                newStop = tsf.createTransitRouteStop(oldStop.getStopFacility(),
                        oldStop.getDepartureOffset().seconds() - departureOffset,
                        oldStop.getDepartureOffset().seconds() - departureOffset);
            } else if (oldStop.getArrivalOffset().isDefined()) {
                newStop = tsf.createTransitRouteStop(oldStop.getStopFacility(),
                        oldStop.getArrivalOffset().seconds() - arrivalOffset,
                        oldStop.getArrivalOffset().seconds() - arrivalOffset);
            } else {
                newStop = tsf.createTransitRouteStop(oldStop.getStopFacility(), 0, 0);
            }

            newStop.setAwaitDepartureTime(oldStop.isAwaitDepartureTime());
            stops.add(newStop);

        }
        return stops;
    }

}