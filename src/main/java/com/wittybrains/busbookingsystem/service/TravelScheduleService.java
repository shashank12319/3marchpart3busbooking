
package com.wittybrains.busbookingsystem.service;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.wittybrains.busbookingsystem.controller.TravelScheduleResponseWrapper;
import com.wittybrains.busbookingsystem.dto.StationDTO;
import com.wittybrains.busbookingsystem.dto.TravelScheduleDTO;
import com.wittybrains.busbookingsystem.exception.StationNotFoundException;
import com.wittybrains.busbookingsystem.model.Station;
import com.wittybrains.busbookingsystem.model.TravelSchedule;
import com.wittybrains.busbookingsystem.repository.StationRepository;
import com.wittybrains.busbookingsystem.repository.TravelScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TravelScheduleService {
	private static final Logger logger = LoggerFactory.getLogger(TravelScheduleService.class);
	private static final int MAX_SEARCH_DAYS = 30;
	private final TravelScheduleRepository scheduleRepository;
	private final StationRepository stationRepository;

	public TravelScheduleService(TravelScheduleRepository scheduleRepository, StationRepository stationRepository) {
		this.scheduleRepository = scheduleRepository;
		this.stationRepository = stationRepository;
	}

	public Station getStationByCode(String code) {

		if (StringUtils.isBlank(code)) {
			throw new IllegalArgumentException("Code must not be null or empty");
		}

		Optional<Station> optionalStation = stationRepository.findByStationCode(code);
		if (optionalStation.isPresent()) {
			return optionalStation.get();
		} else {
			throw new StationNotFoundException("Station with code " + code + " not found");
		}
	}
    public ResponseEntity<TravelScheduleResponseWrapper> getAvailableSchedule(String sourceCode,String destinationCode,String date){
    	
    	if (StringUtils.isEmpty(destinationCode.trim())) {
    	    String message = "Destination station code is null or empty. Source code: " + sourceCode 
    	                     + ", Destination code: " + destinationCode + ", Date: " + date;
    	    TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, null);
    	    logger.warn(message);
    	    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    	}

    	if (StringUtils.isEmpty(sourceCode.trim())) {
    	    String message = "Source station code is null or empty. Source code: " + sourceCode
    	                     + ", Destination code: " + destinationCode + ", Date: " + date;
    	    TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, null);
    	    logger.warn(message);
    	    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    	}

    	if (sourceCode.equals(destinationCode)) {
    	    String message = "Source and destination station codes cannot be the same. Source code: " + sourceCode 
    	                     + ", Destination code: " + destinationCode + ", Date: " + date;
    	    TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, null);
    	    logger.warn(message);
    	    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    	}
    	try {
    	    LocalDate parsedDate = LocalDate.parse(date);
    	    Station source =getStationByCode(sourceCode);
    	    Station destination = getStationByCode(destinationCode);
    	    List<TravelScheduleDTO> schedules = getAvailableSchedules(source, destination, parsedDate);
    	    if (schedules.isEmpty()) {
    	        String message = "No bus is available between " + source.getName() + " and " + destination.getName()
    	                         + " on " + date + ". Source code: " + source
    	                         + ", Destination code: " + destination + ", Date: " + date;
    	        TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, schedules);
    	        logger.info(message);
    	        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    	    } else {
    	        String message = "Available schedules between " + source.getName() + " and " + destination.getName()
    	                         + " on " + date + ". Source code: " + sourceCode 
    	                         + ", Destination code: " + destinationCode + ", Date: " + date;
    	        TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper("", schedules);
    	        logger.info(message);
    	        return new ResponseEntity<>(response, HttpStatus.OK);
    	    }
    	} catch (DateTimeParseException ex) {
    	    String message = "Invalid date format. The correct format is ISO date format (yyyy-MM-dd). Source code: " + sourceCode 
    	                     + ", Destination code: " + destinationCode + ", Date: " + date;
    	    TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, null);
    	    logger.warn(message);
    	    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    	}
    }
    	
    
	public List<TravelScheduleDTO> getAvailableSchedules(Station source, Station destination, LocalDate searchDate) {
		LocalDateTime currentDateTime = LocalDateTime.now();
		LocalDate currentDate = currentDateTime.toLocalDate();
		LocalTime currentTime = currentDateTime.toLocalTime();

		LocalDateTime searchDateTime = LocalDateTime.of(searchDate, LocalTime.MIDNIGHT);
		if (searchDate.isBefore(currentDate)) {
			// cannot search for past schedules
			logger.warn("Cannot search for schedules in the past");
			throw new IllegalArgumentException("Cannot search for schedules in the past");
		} else if (searchDate.equals(currentDate)) {
			// search for schedules at least 1 hour from now
			searchDateTime = LocalDateTime.of(searchDate, currentTime.plusHours(1));
		}

		LocalDateTime maxSearchDateTime = currentDateTime.plusDays(MAX_SEARCH_DAYS);
		if (searchDateTime.isAfter(maxSearchDateTime)) {
			// cannot search for schedules more than one month in the future
			logger.warn("Cannot search for schedules more than one month in the future");
			throw new IllegalArgumentException("Cannot search for schedules more than one month in the future");
		}

		List<TravelSchedule> travelScheduleList = scheduleRepository
				.findBySourceAndDestinationAndEstimatedArrivalTimeAfter(source, destination, currentDateTime);
		List<TravelScheduleDTO> travelScheduleDTOList = new ArrayList<>();
		for (TravelSchedule travelSchedule : travelScheduleList) {
			 //logger.fine(travelSchedule.toString());
			TravelScheduleDTO travelScheduleDTO = new TravelScheduleDTO(travelSchedule);

			travelScheduleDTOList.add(travelScheduleDTO);
		}
		return travelScheduleDTOList;
	}

	public boolean createSchedule(TravelScheduleDTO travelScheduleDTO) throws ParseException {
		logger.info("Creating travel schedule: {}", travelScheduleDTO);

		TravelSchedule travelschedule = new TravelSchedule();

		StationDTO destinationDTO = travelScheduleDTO.getDestination();
		Station destination = getStationByCode(destinationDTO.getStationCode());
		travelschedule.setDestination(destination);

		Station source = getStationByCode(destinationDTO.getStationCode());
		travelschedule.setDestination(source);
		travelschedule = scheduleRepository.save(travelschedule);
		// add log statement
		logger.info("Created travel schedule ");
		return travelschedule.getScheduleId() != null;
	}

	

}