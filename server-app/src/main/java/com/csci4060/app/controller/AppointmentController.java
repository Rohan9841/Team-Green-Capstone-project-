package com.csci4060.app.controller;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import javax.validation.Valid;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;


import com.csci4060.app.ExceptionResolver;

import com.csci4060.app.model.APIresponse;
import com.csci4060.app.model.User;
import com.csci4060.app.model.appointment.Appointment;
import com.csci4060.app.model.appointment.AppointmentDate;
import com.csci4060.app.model.appointment.AppointmentDummy;
import com.csci4060.app.model.appointment.AppointmentResponse;
import com.csci4060.app.model.appointment.AppointmentTime;
import com.csci4060.app.model.appointment.TimeSlotResponse;
import com.csci4060.app.model.appointment.TimeSlots;
import com.csci4060.app.model.calendar.Calendar;
import com.csci4060.app.model.event.Event;
import com.csci4060.app.services.AppointmentDateService;
import com.csci4060.app.services.AppointmentService;
import com.csci4060.app.services.AppointmentTimeService;
import com.csci4060.app.services.CalendarService;
import com.csci4060.app.services.EmailSenderService;
import com.csci4060.app.services.EventService;
import com.csci4060.app.services.TimeSlotsService;
import com.csci4060.app.services.UserService;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping(path = "/api/appointment", produces = "application/json")

public class AppointmentController extends ExceptionResolver {


	@Autowired
	UserService userService;

	@Autowired
	AppointmentService appointmentService;

	@Autowired
	AppointmentDateService appointmentDateService;

	@Autowired
	AppointmentTimeService appointmentTimeService;

	@Autowired
	TimeSlotsService timeSlotsService;

	@Autowired
	private EmailSenderService emailSenderService;

	@Autowired
	CalendarService calendarService;

	@Autowired
	EventService eventService;

	@PostMapping(path = "/set", consumes = "application/json")
	@PreAuthorize("hasRole('PM') or hasRole('ADMIN')")

	public APIresponse setAppointment( @Valid @RequestBody AppointmentDummy appointmentDummy, HttpServletRequest request) {


		List<User> recepientList = new ArrayList<User>();

		List<String> emailFromDummy = appointmentDummy.getRecepients();

		List<String> recepientsEmailList = new ArrayList<String>();

		for (String each : emailFromDummy) {
			User recepient = userService.findByEmail(each);
			if (recepient != null) {
				recepientList.add(recepient);
				recepientsEmailList.add(each);
			}
		}

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		String creatorUsername = "";

		if (principal instanceof UserDetails) {
			creatorUsername = ((UserDetails) principal).getUsername();
		}

		User createdBy = userService.findByUsername(creatorUsername);

		Appointment appointment = new Appointment(appointmentDummy.getName(), appointmentDummy.getDescription(),
				appointmentDummy.getAppdates(), recepientList, createdBy, appointmentDummy.getLocation());

		List<AppointmentDate> dates = appointment.getAppdates();

		//Stores all the timeslots
		List<TimeSlots> timeSlots = new ArrayList<TimeSlots>();

		for (AppointmentDate date : dates) {

			List<AppointmentTime> times = date.getApptimes();

			for (AppointmentTime time : times) {

				LocalTime sTime = LocalTime.parse(time.getStartTime(), DateTimeFormatter.ofPattern("hh:mm a"));
				LocalTime eTime = LocalTime.parse(time.getEndTime(), DateTimeFormatter.ofPattern("hh:mm a"));

				String result1 = LocalTime.parse(sTime.toString(), DateTimeFormatter.ofPattern("HH:mm"))
						.format(DateTimeFormatter.ofPattern("hh:mm a"));
				System.out.println("sTime is: " + result1);

				String result2 = LocalTime.parse(eTime.toString(), DateTimeFormatter.ofPattern("HH:mm"))
						.format(DateTimeFormatter.ofPattern("hh:mm a"));
				System.out.println("sTime is: " + result2);

				long elapsedMinutes = Math.abs(Duration.between(sTime, eTime).toMinutes());

				long maxAppointment = elapsedMinutes / time.getInterv();

				LocalTime slotStartTime = sTime;

				for (int i = 0; i < maxAppointment; i++) {
					LocalTime slotEndTime = slotStartTime.plusMinutes(time.getInterv());
					String timeSlotStart = LocalTime
							.parse(slotStartTime.toString(), DateTimeFormatter.ofPattern("HH:mm"))
							.format(DateTimeFormatter.ofPattern("hh:mm a"));
					String timeSlotEnd = LocalTime.parse(slotEndTime.toString(), DateTimeFormatter.ofPattern("HH:mm"))
							.format(DateTimeFormatter.ofPattern("hh:mm a"));
					// storing each timeslots in a variable instead of directly saving in the
					// database. Because timeslots references AppointmentDate object but we haven't
					// saved the date yet.
					timeSlots.add(new TimeSlots(timeSlotStart, timeSlotEnd, date, appointment, null));
					slotStartTime = slotEndTime;
				}

				// saving the time before date in the database to avoid transient error(appdate
				// references apptime)
				appointmentTimeService.save(time);
			}

			// Saving the date before appointment because appointment has appDates
			appointmentDateService.save(date);
		}
		// saving the appointment in the database
		appointmentService.save(appointment);

		//Going through each timeslots and saving in the database.
		for (TimeSlots slots : timeSlots) {
			timeSlotsService.save(slots);
		}

		if (!recepientsEmailList.isEmpty()) {

			SimpleMailMessage mailMessage = new SimpleMailMessage();

			String[] emails = recepientsEmailList.toArray(new String[recepientsEmailList.size()]);

			mailMessage.setTo(emails);
			mailMessage.setSubject("Appointment Information");
			mailMessage.setFrom("ulmautoemail@gmail.com");
			mailMessage.setText(
					"A faculty has set an appointment for you. Please log in to you ULM communication app and register for the appointment. "
							+ "Thank you!");

			emailSenderService.sendEmail(mailMessage);
		}

		return new APIresponse(HttpStatus.CREATED.value(), "Appointment created successfully", appointment);
	}

	@GetMapping(path = "created/allAppointments")
	@PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
	public APIresponse getFacultyAppointments() {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		String username = "";

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		User user = userService.findByUsername(username);

		List<Appointment> appointments = appointmentService.findAllByCreatedBy(user);

		if (appointments == null) {
			return new APIresponse(HttpStatus.BAD_REQUEST.value(), "You have not created any appointments yet.", null);
		}

		List<AppointmentResponse> allAppointments = new ArrayList<AppointmentResponse>();

		for (Appointment app : appointments) {

			List<String> responseDate = new ArrayList<String>();
			for (AppointmentDate date : app.getAppdates()) {
				responseDate.add(date.getDate());

			}

			allAppointments
					.add(new AppointmentResponse(app.getId(), app.getName(), app.getDescription(), responseDate));

		}

		return new APIresponse(HttpStatus.OK.value(), "All appointments successfully sent.", allAppointments);

	}

	@GetMapping(path = "received/allAppointments")
	@PreAuthorize("hasRole('USER') or hasRole('PM') or hasRole('ADMIN')")
	public APIresponse getAppointments() {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		String username = "";

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		User user = userService.findByUsername(username);

		List<Appointment> appointments = appointmentService.findAllByRecepients(user);

		if (appointments == null) {
			return new APIresponse(HttpStatus.BAD_REQUEST.value(), "You have not created any appointments yet.", null);
		}

		List<AppointmentResponse> allAppointments = new ArrayList<AppointmentResponse>();

		for (Appointment app : appointments) {

			List<String> responseDate = new ArrayList<String>();
			for (AppointmentDate date : app.getAppdates()) {
				responseDate.add(date.getDate());

			}

			allAppointments
					.add(new AppointmentResponse(app.getId(), app.getName(), app.getDescription(), responseDate));

		}

		return new APIresponse(HttpStatus.OK.value(), "All appointments successfully sent.", allAppointments);

	}

	@GetMapping(path = "/timeslots/user/{id}")
	@PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasRole('PM')")
	public APIresponse getSlots(@PathVariable("id") Long appointmentId) {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		String username = "";

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		User user = userService.findByUsername(username);

		Appointment appointment = appointmentService.findById(appointmentId);

		List<TimeSlots> slotsFromAppointment = timeSlotsService.findAllByAppointment(appointment);

		List<TimeSlotResponse> timeSlotResponses = new ArrayList<TimeSlotResponse>();

		for (TimeSlots slots : slotsFromAppointment) {

			if (slots.getSelectedBy() == null) {
				timeSlotResponses.add(new TimeSlotResponse(slots.getId(), slots.getStartTime(), slots.getEndTime(),
						slots.getAppdates().getDate(), appointment.getName(), appointment.getDescription(),
						appointment.getCreatedBy().getName()));
			} else if (slots.getSelectedBy() == user) {
				return new APIresponse(HttpStatus.OK.value(), "User has already selected a slot.", null);
			}
		}

		return new APIresponse(HttpStatus.OK.value(), "Time slots successfully sent.", timeSlotResponses);
	}

	@GetMapping(path = "/timeslots/faculty/{id}")
	@PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
	public APIresponse getSlotsForFaculty(@PathVariable("id") Long appointmentId) {

		Appointment appointment = appointmentService.findById(appointmentId);

		List<TimeSlots> slotsFromAppointment = timeSlotsService.findAllByAppointment(appointment);

		List<TimeSlotResponse> timeSlotResponses = new ArrayList<TimeSlotResponse>();

		for (TimeSlots slots : slotsFromAppointment) {

			String selectorName = "Not selected";
			String selectorEmail = "Not selected";

			if (slots.getSelectedBy() != null) {

				User selectedBy = slots.getSelectedBy();

				selectorName = selectedBy.getName();
				selectorEmail = selectedBy.getEmail();

			}

			timeSlotResponses.add(new TimeSlotResponse(slots.getId(), slots.getStartTime(), slots.getEndTime(),
					slots.getAppdates().getDate(), selectorName, selectorEmail, appointment.getName(),
					appointment.getDescription(), appointment.getCreatedBy().getName()));

		}

		return new APIresponse(HttpStatus.OK.value(), "Time slots successfully sent.", timeSlotResponses);
	}

	@PostMapping(path = "timeslots/postSlot/{timeSlotId}", produces = "application/json")
	@PreAuthorize("hasRole('USER') or hasRole('PM') or hasRole('ADMIN')")
	public APIresponse postSlots(@PathVariable("timeSlotId") long id) {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		String username = "";

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		User selectedBy = userService.findByUsername(username);

		TimeSlots slotToRemove = timeSlotsService.findById(id);

		if (slotToRemove == null) {
			throw new ResourceAccessException("There is no timeslot with given id " + id + " in the database");
		}

		slotToRemove.setSelectedBy(selectedBy);
		timeSlotsService.save(slotToRemove);

		Event creatorsEvent = eventService.findByTimeSlotId(id);

		System.out.println("Event from timeslot: " + creatorsEvent);

		Calendar calendar = calendarService.findByNameAndCreatedBy("Appointment", selectedBy);

		System.out.println("calendar before adding event" + calendar);

		calendar.addEvent(creatorsEvent);

		System.out.println("calendar after adding event" + calendar);

		calendarService.save(calendar);

		TimeSlotResponse response = new TimeSlotResponse(slotToRemove.getId(), slotToRemove.getStartTime(),
				slotToRemove.getEndTime(), slotToRemove.getAppdates().getDate(), selectedBy.getName(),
				selectedBy.getEmail());

		return new APIresponse(HttpStatus.GONE.value(), "User has selected the timeslot.", response);
	}

//	@GetMapping(path = "/sendToCalendar", produces = "application/json")
//	@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
//	public APIresponse getCalendarAppointments() {
//		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//
//		String username = "";
//
//		if (principal instanceof UserDetails) {
//			username = ((UserDetails) principal).getUsername();
//		}
//
//		User currentUser = userService.findByUsername(username);
//		Set<Role> role = currentUser.getRoles();
//
//		Boolean contains = false;
//		Iterator<Role> value = role.iterator();
//
//		while (value.hasNext()) {
//			Role role2 = (Role) value.next();
//
//			if (role2.getName().toString().equals("ROLE_USER")) {
//				contains = true;
//			}
//
//		}
//
//		if (contains) {
//			List<TimeSlots> slotsToCalendar = timeSlotsService.findAllBySelectedBy(currentUser);
//			List<TimeSlotResponse> slotResponses = new ArrayList<TimeSlotResponse>();
//
//			if (slotsToCalendar.size() == 0) {
//				return new APIresponse(HttpStatus.OK.value(), "User doesnt have any appointments right now!", null);
//			}
//
//			else {
//				for (TimeSlots timeSlots : slotsToCalendar) {
//					slotResponses.add(new TimeSlotResponse(timeSlots.getStartTime(), timeSlots.getEndTime(),
//							timeSlots.getAppdates().getDate(), timeSlots.getAppointment().getName(),
//							timeSlots.getAppointment().getDescription(),
//							timeSlots.getAppointment().getCreatedBy().getName()));
//
//				}
//				return new APIresponse(HttpStatus.OK.value(),
//						"All  selected time slots from appointments successfully sent.", slotResponses);
//			}
//
//		}
//
//		else {
//			List<TimeSlots> slotsToCalendar = new ArrayList<TimeSlots>();
//			List<TimeSlotResponse> slotResponses = new ArrayList<TimeSlotResponse>();
//
//			List<Appointment> appoi = appointmentService.findAllByCreatedBy(currentUser);
//			for (Appointment appo : appoi) {
//				List<TimeSlots> slots = timeSlotsService.findAllByAppointment(appo);
//				slotsToCalendar.addAll(slots);
//			}
//
//			if (slotsToCalendar.size() == 0) {
//				return new APIresponse(HttpStatus.OK.value(), "User doesnt have any appointments right now!", null);
//			}
//
//			else {
//
//				for (TimeSlots timeSlots : slotsToCalendar) {
//					slotResponses.add(new TimeSlotResponse(timeSlots.getStartTime(), timeSlots.getEndTime(),
//							timeSlots.getAppdates().getDate(), timeSlots.getAppointment().getName(),
//							timeSlots.getAppointment().getDescription(),
//							timeSlots.getAppointment().getCreatedBy().getName()));
//
//				}
//				return new APIresponse(HttpStatus.OK.value(),
//						"All  selected time slots from appointments successfully sent.", slotResponses);
//			}
//
//		}
//
//	}

	@PostMapping(path = "/sendToCalendar/{appointmentId}", produces = "application/json")
	@PreAuthorize("hasRole('PM') or hasRole('ADMIN')")

	public APIresponse sendToCalendar(@Valid @PathVariable("appointmentId") long id) {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		String username = "";

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		User currentUser = userService.findByUsername(username);

		Appointment appointment = appointmentService.findById(id);

		if (appointment == null) {
			throw new ResourceAccessException("There is no appointment with id " + id + " in the database");
		}

		List<TimeSlots> timeSlots = timeSlotsService.findAllByAppointment(appointment);

		List<Event> eventList = new ArrayList<Event>();

		Calendar calendar = calendarService.findByNameAndCreatedBy("Appointment", currentUser);

		System.out.println(appointment);

		for (TimeSlots slot : timeSlots) {

			AppointmentDate appointmentDate = slot.getAppdates();

			String startTime = appointmentDate.getDate() + " " + slot.getStartTime();
			System.out.println(startTime);

			String endTime = appointmentDate.getDate() + " " + slot.getEndTime();
			System.out.println(endTime);

			Event event = new Event(appointment.getName(), appointment.getDescription(), appointment.getLocation(),

					null, startTime, endTime, currentUser, false, calendar.getColor(), "", slot.getId());


			eventList.add(event);
			System.out.println(event);
			eventService.save(event);
			calendar.addEvent(event);
			calendarService.save(calendar);
		}

		return new APIresponse(HttpStatus.CREATED.value(), "Appointment has been successfully saved to calendar",
				eventList);

	}

	@GetMapping(path = "/getScheduledAppointments", produces = "application/json")
	@PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
	public APIresponse scheduledAppointmentsByUser() {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String username = "";
		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}
		User currentUser = userService.findByUsername(username);

		List<TimeSlots> allSlotsFromAppointment = new ArrayList<TimeSlots>();
		List<Appointment> allAppointments = appointmentService.findAllByCreatedBy(currentUser);
		
		if (allAppointments == null)
		{
			return new APIresponse(HttpStatus.BAD_REQUEST.value(), "You have not created any appointment! Please create appointment and assign appointees!",
					null);
		}
		else
		{
			for (Appointment app : allAppointments) {
				allSlotsFromAppointment.addAll(timeSlotsService.findAllByAppointment(app));
			}
	
			List<TimeSlotResponse> slotResponses = new ArrayList<TimeSlotResponse>();
			for (TimeSlots slots : allSlotsFromAppointment) {
				if (slots.getSelectedBy() != null) {
					slotResponses.add(new TimeSlotResponse(slots.getId(), slots.getStartTime(), slots.getEndTime(),
							slots.getAppdates().getDate(), slots.getSelectedBy().getName(),
							slots.getSelectedBy().getEmail(), slots.getAppointment().getName(),
							slots.getAppointment().getDescription(), slots.getAppointment().getCreatedBy().getName()));
				}
			}
			return new APIresponse(HttpStatus.OK.value(), "All  selected time slots from appointments successfully sent.",
					slotResponses);
		}

	}

	@GetMapping(path = "/getScheduledAppointmentsUser", produces = "application/json")
	@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
	public APIresponse scheduledAppointmentsForUser() {

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String username = "";
		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}
		User currentUser = userService.findByUsername(username);

		List<TimeSlots> allSlotsFromAppointment = new ArrayList<TimeSlots>();
		for (Appointment app : appointmentService.findAllByRecepients(currentUser)) {
			allSlotsFromAppointment.addAll(timeSlotsService.findAllByAppointment(app));
		}

		List<TimeSlotResponse> slotResponses = new ArrayList<TimeSlotResponse>();
		for (TimeSlots slots : allSlotsFromAppointment) {
			if (slots.getSelectedBy() == currentUser) {
				slotResponses.add(new TimeSlotResponse(slots.getId(), slots.getStartTime(), slots.getEndTime(),
						slots.getAppdates().getDate(), slots.getSelectedBy().getName(),
						slots.getSelectedBy().getEmail(), slots.getAppointment().getName(),
						slots.getAppointment().getDescription(), slots.getAppointment().getCreatedBy().getName()));
			}
		}
		return new APIresponse(HttpStatus.OK.value(), "All  selected time slots from appointments successfully sent.",
				slotResponses);

	}

}
