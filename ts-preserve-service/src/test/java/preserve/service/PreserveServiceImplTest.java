package preserve.service;

import edu.fudan.common.util.Response;
import edu.fudan.common.util.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.mockito.MockedStatic;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import edu.fudan.common.entity.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

@RunWith(JUnit4.class)
public class PreserveServiceImplTest {

    @InjectMocks
    private PreserveServiceImpl preserveServiceImpl;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private preserve.mq.RabbitSend sendService;

    @Mock
    private org.springframework.cloud.client.discovery.DiscoveryClient discoveryClient;

    private HttpHeaders headers = new HttpHeaders();
    private HttpEntity requestEntity = new HttpEntity(headers);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPreserve() {
        OrderTicketsInfo oti = OrderTicketsInfo.builder()
                .accountId(UUID.randomUUID().toString())
                .contactsId(UUID.randomUUID().toString())
                .from("from_station")
                .to("to_station")
                .date(StringUtils.Date2String(new Date()))
                .handleDate("handle_date")
                .tripId("G1255")
                .seatType(2)
                .assurance(1)
                .foodType(1)
                .foodName("food_name")
                .foodPrice(1.0)
                .stationName("station_name")
                .storeName("store_name")
                .consigneeName("consignee_name")
                .consigneePhone("123456789")
                .consigneeWeight(1.0)
                .isWithin(true)
                .build();

        //response for checkSecurity()、addAssuranceForOrder()、createFoodOrder()、createConsign()
        Response response1 = new Response<>(1, null, null);
        ResponseEntity<Response> re1 = new ResponseEntity<>(response1, HttpStatus.OK);

        //response for sendEmail()
        ResponseEntity<Boolean> re10 = new ResponseEntity<>(true, HttpStatus.OK);

        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(Class.class)))
                .thenReturn(re1).thenReturn(re1).thenReturn(re1).thenReturn(re1).thenReturn(re10);


        //response for getContactsById()
        Contacts contacts = new Contacts();
        contacts.setDocumentNumber("document_number");
        contacts.setName("name");
        contacts.setDocumentType(1);
        Response<Contacts> response2 = new Response<>(1, null, contacts);
        ResponseEntity<Response<Contacts>> re2 = new ResponseEntity<>(response2, HttpStatus.OK);

        //response for getTripAllDetailInformation()
        TripResponse tripResponse = new TripResponse();
        tripResponse.setConfortClass(1);
        tripResponse.setStartTime(StringUtils.Date2String(new Date()));
        TripAllDetail tripAllDetail = new TripAllDetail(true, "message", tripResponse, new Trip());
        Response<TripAllDetail> response3 = new Response<>(1, null, tripAllDetail);
        ResponseEntity<Response<TripAllDetail>> re3 = new ResponseEntity<>(response3, HttpStatus.OK);

        //response for queryForStationId()
        Response<String> response4 = new Response<>(null, null, "");
        ResponseEntity<Response<String>> re4 = new ResponseEntity<>(response4, HttpStatus.OK);

        //response for travel result
        TravelResult travelResult = new TravelResult();
        Route route = new Route();
        route.setStations(new ArrayList<>());
        travelResult.setRoute(route);
        TrainType trainType = new TrainType();
        trainType.setConfortClass(100);
        trainType.setEconomyClass(100);
        travelResult.setTrainType(trainType);
        travelResult.setPrices( new HashMap<String, String>(){{ put("confortClass", "1.0"); put("economyClass", "0.5"); }} );
        Response<TravelResult> response5 = new Response<>(1, null, travelResult);
        ResponseEntity<Response<TravelResult>> re5 = new ResponseEntity<>(response5, HttpStatus.OK);

        //response for dipatchSeat()
        Ticket ticket = new Ticket();
        ticket.setSeatNo(1);
        Response<Ticket> response6 = new Response<>(1, null, ticket);
        ResponseEntity<Response<Ticket>> re6 = new ResponseEntity<>(response6, HttpStatus.OK);

        //response for createOrder()
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setAccountId(UUID.randomUUID().toString());
        order.setTravelDate(StringUtils.Date2String(new Date()));
        order.setFrom("from_station");
        order.setTo("to_station");
        Response<Order> response7 = new Response<>(1, null, order);
        ResponseEntity<Response<Order>> re7 = new ResponseEntity<>(response7, HttpStatus.OK);

        //response for getAccount()
        User user = new User();
        user.setEmail("email");
        user.setUserName("user_name");
        Response<User> response9 = new Response<>(1, null, user);
        ResponseEntity<Response<User>> re9 = new ResponseEntity<>(response9, HttpStatus.OK);

        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(ParameterizedTypeReference.class)))
                .thenReturn(re2).thenReturn(re3).thenReturn(re5).thenReturn(re6).thenReturn(re7).thenReturn(re9);

        try (MockedStatic<ActiveSpan> activeSpan = Mockito.mockStatic(ActiveSpan.class)) {
            Response result = preserveServiceImpl.preserve(oti, headers);
            Assert.assertEquals(new Response<>(1, "Success.", null), result);

            activeSpan.verify(() -> ActiveSpan.tag("workflow", "preserve"));
            activeSpan.verify(() -> ActiveSpan.tag("tripId", "G1255"));
            activeSpan.verify(() -> ActiveSpan.tag("seatTypeRequested", "2"));
            activeSpan.verify(() -> ActiveSpan.tag("security.status", "pass"));
            activeSpan.verify(() -> ActiveSpan.tag("seat.confortAvailable", "1"));
            activeSpan.verify(() -> ActiveSpan.tag("seat.economyAvailable", "0"));
            activeSpan.verify(() -> ActiveSpan.tag("seat.checkResult", "pass"));
            activeSpan.verify(() -> ActiveSpan.tag("price.confortClass", "1.0"));
            activeSpan.verify(() -> ActiveSpan.tag("price.economyClass", "0.5"));
        }
    }

    @Test
    public void testPreserve_securityCheckFailed() {
        OrderTicketsInfo oti = OrderTicketsInfo.builder()
                .accountId(UUID.randomUUID().toString())
                .contactsId(UUID.randomUUID().toString())
                .from("from_station")
                .to("to_station")
                .date(StringUtils.Date2String(new Date()))
                .tripId("G1255")
                .seatType(2)
                .build();

        Response securityFail = new Response<>(0, "Security check failed", null);
        ResponseEntity<Response> reSecurityFail = new ResponseEntity<>(securityFail, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(Class.class)))
                .thenReturn(reSecurityFail);

        try (MockedStatic<ActiveSpan> activeSpan = Mockito.mockStatic(ActiveSpan.class)) {
            Response result = preserveServiceImpl.preserve(oti, headers);
            Assert.assertEquals(new Response<>(0, "Security check failed", null), result);

            activeSpan.verify(() -> ActiveSpan.tag("security.status", "fail"));
            activeSpan.verify(Mockito.never(), () -> ActiveSpan.tag(Mockito.eq("seat.checkResult"), Mockito.anyString()));
        }
    }

    @Test
    public void testPreserve_seatNotEnough_firstClass() {
        OrderTicketsInfo oti = OrderTicketsInfo.builder()
                .accountId(UUID.randomUUID().toString())
                .contactsId(UUID.randomUUID().toString())
                .from("from_station")
                .to("to_station")
                .date(StringUtils.Date2String(new Date()))
                .tripId("G1255")
                .seatType(2)
                .build();

        Response securityPass = new Response<>(1, null, null);
        ResponseEntity<Response> reSecurityPass = new ResponseEntity<>(securityPass, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(Class.class)))
                .thenReturn(reSecurityPass);

        Contacts contacts = new Contacts();
        contacts.setDocumentNumber("document_number");
        contacts.setName("name");
        contacts.setDocumentType(1);
        Response<Contacts> contactsResponse = new Response<>(1, null, contacts);
        ResponseEntity<Response<Contacts>> reContacts = new ResponseEntity<>(contactsResponse, HttpStatus.OK);

        TripResponse tripResponse = new TripResponse();
        tripResponse.setConfortClass(0);
        TripAllDetail tripAllDetail = new TripAllDetail(true, "message", tripResponse, new Trip());
        Response<TripAllDetail> tripDetailResponse = new Response<>(1, null, tripAllDetail);
        ResponseEntity<Response<TripAllDetail>> reTripDetail = new ResponseEntity<>(tripDetailResponse, HttpStatus.OK);

        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(ParameterizedTypeReference.class)))
                .thenReturn(reContacts).thenReturn(reTripDetail);

        try (MockedStatic<ActiveSpan> activeSpan = Mockito.mockStatic(ActiveSpan.class)) {
            Response result = preserveServiceImpl.preserve(oti, headers);
            Assert.assertEquals(new Response<>(0, "Seat Not Enough", null), result);

            activeSpan.verify(() -> ActiveSpan.tag("seat.confortAvailable", "0"));
            activeSpan.verify(() -> ActiveSpan.tag("seat.checkResult", "not_enough"));
            activeSpan.verify(Mockito.never(), () -> ActiveSpan.tag(Mockito.eq("price.confortClass"), Mockito.any()));
        }
    }

    @Test
    public void testPreserve_seatNotEnough_secondClass() {
        OrderTicketsInfo oti = OrderTicketsInfo.builder()
                .accountId(UUID.randomUUID().toString())
                .contactsId(UUID.randomUUID().toString())
                .from("from_station")
                .to("to_station")
                .date(StringUtils.Date2String(new Date()))
                .tripId("G1255")
                .seatType(3)
                .build();

        Response securityPass = new Response<>(1, null, null);
        ResponseEntity<Response> reSecurityPass = new ResponseEntity<>(securityPass, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(Class.class)))
                .thenReturn(reSecurityPass);

        Contacts contacts = new Contacts();
        contacts.setDocumentNumber("document_number");
        contacts.setName("name");
        contacts.setDocumentType(1);
        Response<Contacts> contactsResponse = new Response<>(1, null, contacts);
        ResponseEntity<Response<Contacts>> reContacts = new ResponseEntity<>(contactsResponse, HttpStatus.OK);

        TripResponse tripResponse = new TripResponse();
        tripResponse.setConfortClass(0);
        tripResponse.setEconomyClass(3);
        TripAllDetail tripAllDetail = new TripAllDetail(true, "message", tripResponse, new Trip());
        Response<TripAllDetail> tripDetailResponse = new Response<>(1, null, tripAllDetail);
        ResponseEntity<Response<TripAllDetail>> reTripDetail = new ResponseEntity<>(tripDetailResponse, HttpStatus.OK);

        Mockito.when(restTemplate.exchange(
                Mockito.anyString(),
                Mockito.any(HttpMethod.class),
                Mockito.any(HttpEntity.class),
                Mockito.any(ParameterizedTypeReference.class)))
                .thenReturn(reContacts).thenReturn(reTripDetail);

        try (MockedStatic<ActiveSpan> activeSpan = Mockito.mockStatic(ActiveSpan.class)) {
            Response result = preserveServiceImpl.preserve(oti, headers);
            Assert.assertEquals(new Response<>(0, "Seat Not Enough", null), result);

            activeSpan.verify(() -> ActiveSpan.tag("seat.economyAvailable", "3"));
            activeSpan.verify(() -> ActiveSpan.tag("seat.checkResult", "not_enough"));
        }
    }

    @Test
    public void testDipatchSeat() {
        long mills = System.currentTimeMillis();
        Seat seatRequest = new Seat(StringUtils.Date2String(new Date()), "G1234", "start_station", "dest_station", 2, 100, null);
        HttpEntity requestEntityTicket = new HttpEntity(seatRequest, headers);
        Response<Ticket> response = new Response<>();
        ResponseEntity<Response<Ticket>> reTicket = new ResponseEntity<>(response, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                "http://ts-seat-service:18898/api/v1/seatservice/seats",
                HttpMethod.POST,
                requestEntityTicket,
                new ParameterizedTypeReference<Response<Ticket>>() {
                })).thenReturn(reTicket);
        Ticket result = preserveServiceImpl.dipatchSeat(StringUtils.Date2String(new Date()), "G1234", "start_station", "dest_station", 2, 100, null, headers);
        Assert.assertNull(result);
    }

    @Test
    public void testSendEmail() {
        NotifyInfo notifyInfo = new NotifyInfo();
        HttpEntity requestEntitySendEmail = new HttpEntity(notifyInfo, headers);
        ResponseEntity<Boolean> reSendEmail = new ResponseEntity<>(true, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                "http://ts-notification-service:17853/api/v1/notifyservice/notification/preserve_success",
                HttpMethod.POST,
                requestEntitySendEmail,
                Boolean.class)).thenReturn(reSendEmail);
        boolean result = preserveServiceImpl.sendEmail(notifyInfo, headers);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAccount() {
        Response<User> response = new Response<>();
        ResponseEntity<Response<User>> re = new ResponseEntity<>(response, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                "http://ts-user-service:12342/api/v1/userservice/users/id/1",
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Response<User>>() {
                })).thenReturn(re);
        User result = preserveServiceImpl.getAccount("1", headers);
        Assert.assertNull(result);
    }

}
