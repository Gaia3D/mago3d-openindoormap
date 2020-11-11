package io.openindoormap.service.impl;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.*;
import de.fraunhofer.iosb.ilt.sta.model.builder.*;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractDatastreamBuilder;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractFeatureOfInterestBuilder;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractLocationBuilder;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractSensorBuilder;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import io.openindoormap.config.PropertiesConfig;
import io.openindoormap.domain.sensor.AirQuality;
import io.openindoormap.service.AirQualityService;
import io.openindoormap.support.LogMessageSupport;
import io.openindoormap.utils.SensorThingsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geojson.Feature;
import org.geojson.Point;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * sensor 초기 데이터 생성 및 갱신
 */
@Service("airQualityService")
@RequiredArgsConstructor
@Slf4j
public class AirQualityServiceImpl implements AirQualityService {

    private final PropertiesConfig propertiesConfig;

    private SensorThingsUtils sta;
    private SensorThingsService service;
    private boolean ObservedPropertyExist = false;

    @PostConstruct
    public void postConstruct() {
        sta = new SensorThingsUtils();
        sta.init(propertiesConfig.getSensorThingsApiServer());
        service = sta.getService();
    }

    /**
     * 초기 저장소 데이터 insert & update
     */
    @Override
    public void initSensorData() {
        JSONObject stationJson = null;
        try {
            // things 의 모든 available 값을 false 처리
            updateAirQualityThingsStatus();
            // ObservedProperty init
            initObservedProperty();
            // 저장소 목록
            stationJson = getListStation();
        } catch (Exception e) {
            LogMessageSupport.printMessage(e, "-------- AirQualityService Error = {}", e.getMessage());
        }
        List<?> stationList = (List<?>) stationJson.get("list");
        for (var station : stationList) {
            Map<String, Object> thingProperties = new HashMap<>();
            var json = (JSONObject) station;
            var stationName = json.get("stationName").toString();
            var dmX = json.get("dmX").toString().trim();
            var dmY = json.get("dmY").toString().trim();
            var mangName = json.get("mangName").toString();
            var addr = json.get("addr").toString();

            thingProperties.put("stationName", stationName);
            thingProperties.put("year", json.get("year"));
            thingProperties.put("oper", json.get("oper"));
            thingProperties.put("photo", json.get("photo"));
            thingProperties.put("vrml", json.get("vrml"));
            thingProperties.put("map", json.get("map"));
            thingProperties.put("mangName", mangName);
            thingProperties.put("item", json.get("item"));
            thingProperties.put("available", true);

            var thing = sta.hasThing(null, stationName);
            var location = sta.hasLocation(null, addr);
            var thingExist = thing != null;
            Point point = null;
            if (!"".equals(dmX) && !"".equals(dmY)) {
                point = new Point(Double.parseDouble(dmY), Double.parseDouble(dmX));
            }
            Feature feature = new Feature();
            feature.setGeometry(point);
            List<Location> locationList = new ArrayList<>();
            // 마지막 things id 조회
            var thingId = thing == null ? 0 : Long.parseLong(thing.getId().toString());
            var locationId = location == null ? 0 : Long.parseLong(location.getId().toString());
            var dataStreamId = (thingId - 1) * 6;
            var sensorId = (thingId - 1) * 6;
            var ObservedPropertyId = 1L;
            List<Entity> entityList = new ArrayList<>();
            // Location
            location = LocationBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(locationId)))
                    .name(addr)
                    .encodingType(AbstractLocationBuilder.ValueCode.GeoJSON)
                    .description("대기질 측정소 위치")
                    .location(feature)
                    .build();

            if (point != null) {
                entityList.add(location);
                locationList.add(location);
            }

            thing = ThingBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(thingId)))
                    .name(stationName)
                    .description("한국환경공단 측정소")
                    .properties(thingProperties)
                    .locations(locationList)
                    .build();
            entityList.add(thing);

            Sensor sensorPm10 = SensorBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++sensorId)))
                    .name(stationName + ":" + "미세먼지(PM10)")
                    .description("미세먼지 측정소")
                    .encodingType(AbstractSensorBuilder.ValueCode.SensorML)
                    .metadata(mangName)
                    .build();
            entityList.add(sensorPm10);

            // DataStream PM10
            Datastream dataStreamPm10 = DatastreamBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++dataStreamId)))
                    .name(AirQuality.PM10.getDatastreamName())
                    .description(AirQuality.PM10.getDatastreamName())
                    .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                    .unitOfMeasurement(new UnitOfMeasurement(
                            "microgram per cubic meter",
                            "㎍/m³",
                            "https://www.eea.europa.eu/themes/air/air-quality/resources/glossary/g-m3"
                    ))
                    .sensor(sensorPm10)
                    .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse(String.valueOf(ObservedPropertyId++))).build())
                    .thing(thing)
                    .build();
            entityList.add(dataStreamPm10);

            Sensor sensorPm25 = SensorBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++sensorId)))
                    .name(stationName + ":" + "미세먼지(PM2.5)")
                    .description("미세먼지 측정소")
                    .encodingType(AbstractSensorBuilder.ValueCode.SensorML)
                    .metadata(mangName)
                    .build();
            entityList.add(sensorPm25);

            // DataStream PM2.5
            Datastream dataStreamPm25 = DatastreamBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++dataStreamId)))
                    .name(AirQuality.PM25.getDatastreamName())
                    .description(AirQuality.PM25.getDatastreamName())
                    .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                    .unitOfMeasurement(new UnitOfMeasurement(
                            "microgram per cubic meter",
                            "㎍/m³",
                            "https://www.eea.europa.eu/themes/air/air-quality/resources/glossary/g-m3"
                    ))
                    .sensor(sensorPm25)
                    .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse(String.valueOf(ObservedPropertyId++))).build())
                    .thing(thing)
                    .build();
            entityList.add(dataStreamPm25);

            Sensor sensorSo2 = SensorBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++sensorId)))
                    .name(stationName + ":" + "아황산가스 농도")
                    .description("미세먼지 측정소")
                    .encodingType(AbstractSensorBuilder.ValueCode.SensorML)
                    .metadata(mangName)
                    .build();
            entityList.add(sensorSo2);

            // DataStream 아황산가스 농도
            Datastream dataStreamSo2 = DatastreamBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++dataStreamId)))
                    .name(AirQuality.SO2.getDatastreamName())
                    .description(AirQuality.SO2.getDatastreamName())
                    .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                    .unitOfMeasurement(new UnitOfMeasurement(
                            "parts per million",
                            "ppm",
                            "https://en.wikipedia.org/wiki/Parts-per_notation"
                    ))
                    .sensor(sensorSo2)
                    .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse(String.valueOf(ObservedPropertyId++))).build())
                    .thing(thing)
                    .build();
            entityList.add(dataStreamSo2);

            Sensor sensorCo = SensorBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++sensorId)))
                    .name(stationName + ":" + "일산화탄소 농도")
                    .description("미세먼지 측정소")
                    .encodingType(AbstractSensorBuilder.ValueCode.SensorML)
                    .metadata(mangName)
                    .build();
            entityList.add(sensorCo);

            // DataStream 일산화탄소 농도
            Datastream dataStreamCo = DatastreamBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++dataStreamId)))
                    .name(AirQuality.CO.getDatastreamName())
                    .description(AirQuality.CO.getDatastreamName())
                    .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                    .unitOfMeasurement(new UnitOfMeasurement(
                            "parts per million",
                            "ppm",
                            "https://en.wikipedia.org/wiki/Parts-per_notation"
                    ))
                    .sensor(sensorCo)
                    .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse(String.valueOf(ObservedPropertyId++))).build())
                    .thing(thing)
                    .build();
            entityList.add(dataStreamCo);

            Sensor sensorO3 = SensorBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++sensorId)))
                    .name(stationName + ":" + "오존 농도")
                    .description("미세먼지 측정소")
                    .encodingType(AbstractSensorBuilder.ValueCode.SensorML)
                    .metadata(mangName)
                    .build();
            entityList.add(sensorO3);

            // DataStream 오존 농도
            Datastream dataStreamO3 = DatastreamBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++dataStreamId)))
                    .name(AirQuality.O3.getDatastreamName())
                    .description(AirQuality.O3.getDatastreamName())
                    .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                    .unitOfMeasurement(new UnitOfMeasurement(
                            "parts per million",
                            "ppm",
                            "https://en.wikipedia.org/wiki/Parts-per_notation"
                    ))
                    .sensor(sensorO3)
                    .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse(String.valueOf(ObservedPropertyId++))).build())
                    .thing(thing)
                    .build();
            entityList.add(dataStreamO3);

            Sensor sensorNo2 = SensorBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++sensorId)))
                    .name(stationName + ":" + "이산화질소 농도")
                    .description("미세먼지 측정소")
                    .encodingType(AbstractSensorBuilder.ValueCode.SensorML)
                    .metadata(mangName)
                    .build();
            entityList.add(sensorNo2);

            // DataStream 이산화질소 농도
            Datastream dataStreamNo2 = DatastreamBuilder.builder()
                    .id(Id.tryToParse(String.valueOf(++dataStreamId)))
                    .name(AirQuality.NO2.getDatastreamName())
                    .description(AirQuality.NO2.getDatastreamName())
                    .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                    .unitOfMeasurement(new UnitOfMeasurement(
                            "parts per million",
                            "ppm",
                            "https://en.wikipedia.org/wiki/Parts-per_notation"
                    ))
                    .sensor(sensorNo2)
                    .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse(String.valueOf(ObservedPropertyId))).build())
                    .thing(thing)
                    .build();
            entityList.add(dataStreamNo2);

            // FeatureOfInterest
            FeatureOfInterest featureOfInterest = FeatureOfInterestBuilder.builder()
                    .id(thing.getId())
                    .name(stationName + " 측정소")
                    .description("한국환경공단 대기질 측정소")
                    .encodingType(AbstractFeatureOfInterestBuilder.ValueCode.GeoJSON)
                    .feature(feature)
                    .build();
            entityList.add(featureOfInterest);

            try {
                for (var entity : entityList) {
                    if (thingExist) {
                        service.update(entity);
                    } else {
                        service.create(entity);
                    }
                }
            } catch (Exception e) {
                LogMessageSupport.printMessage(e, "-------- AirQualityService create & update Error = {}", e.getMessage());
            }
        }
    }

    /**
     * 미세먼지 측정 데이터 insert
     */
    @Override
    public void insertSensorData() {
        JSONObject stationJson = null;
        try {
            stationJson = getListStation();
        } catch (Exception e) {
            LogMessageSupport.printMessage(e, "-------- AirQualityService Error = {}", e.getMessage());
        }

        List<?> stationList = (List<?>) stationJson.get("list");
        for (var station : stationList) {
            var jsonObject = (JSONObject) station;
            var stationName = (String) jsonObject.get("stationName");
            JSONObject json = new JSONObject();
            try {
//                for(int i=0; i< 24;i++) {
                JSONObject result = getAirQualityData(stationName);
                LocalDateTime t = LocalDateTime.parse((String) result.get("dataTime"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
//                    t = t.minusDays(1);
//                    t = t.plusHours(i);
                ZonedDateTime zonedDateTime = ZonedDateTime.of(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), 0, 0, 0, ZoneId.of("Asia/Seoul"));
                EntityList<Thing> things = service.things()
                        .query()
                        .filter("name eq " + "'" + stationName + "'")
                        .expand("Datastreams($orderby=id asc)/Observations($orderby=id desc)")
                        .list();
                // 일치하는 thing 이 없을경우 skip
                if (things.size() == 0) continue;

                Thing thing = things.toList().get(0);
                EntityList<Datastream> datastreamList = thing.getDatastreams();
                for (var datastream : datastreamList) {
                    var name = datastream.getName();
                    if (name.equals(AirQuality.PM10.getDatastreamName())) {
                        json.put("value", result.get("pm10Value"));
                        json.put("grade", result.get("pm10Grade"));
                    } else if (name.equals(AirQuality.PM25.getDatastreamName())) {
                        json.put("value", result.get("pm25Value"));
                        json.put("grade", result.get("pm25Grade"));
                    } else if (name.equals(AirQuality.SO2.getDatastreamName())) {
                        json.put("value", result.get("so2Value"));
                        json.put("grade", result.get("so2Grade"));
                    } else if (name.equals(AirQuality.CO.getDatastreamName())) {
                        json.put("value", result.get("coValue"));
                        json.put("grade", result.get("coGrade"));
                    } else if (name.equals(AirQuality.O3.getDatastreamName())) {
                        json.put("value", result.get("o3Value"));
                        json.put("grade", result.get("o3Grade"));
                    } else if (name.equals(AirQuality.NO2.getDatastreamName())) {
                        json.put("value", result.get("no2Value"));
                        json.put("grade", result.get("no2Grade"));
                    }

                    Observation observation = ObservationBuilder.builder()
                            .phenomenonTime(new TimeObject(ZonedDateTime.now()))
                            .resultTime(zonedDateTime)
                            .result(json)
                            .datastream(DatastreamBuilder.builder().id(Id.tryToParse(String.valueOf(datastream.getId()))).build())
                            .featureOfInterest(FeatureOfInterestBuilder.builder().id(Id.tryToParse(String.valueOf(thing.getId()))).build())
                            .build();

                    var observationCount = datastream.getObservations().size();
                    var lastObservation = observationCount > 0 ? datastream.getObservations().toList().get(0) : null;
                    var lastTime = lastObservation != null ? lastObservation.getResultTime().withZoneSameInstant(ZoneId.of("Asia/Seoul")) : null;
                    if (zonedDateTime.equals(lastTime)) {
                        observation.setId(lastObservation.getId());
                        service.update(observation);
                    } else {
                        service.create(observation);
                    }
                }
//                }
            } catch (Exception e) {
                LogMessageSupport.printMessage(e, "-------- AirQualityService Error = {}", e.getMessage());
            }
        }
    }

    /**
     * 측정대상 정보 insert
     *
     * @throws ServiceFailureException
     */
    private void initObservedProperty() throws ServiceFailureException {
        // ObservedProperty 는 고정이므로 한번만 체크해서 넣고 그 다음부터는 skip
        if (ObservedPropertyExist) {
            log.info("================== ObservedProperty exist ==================");
            return;
        }

        ObservedProperty pm10 = sta.hasObservedProperty(null, AirQuality.PM10.getObservedPropertyName());
        if (pm10 == null) {
            // ObservedProperty PM10
            service.create(ObservedPropertyBuilder.builder()
                    .name(AirQuality.PM10.getObservedPropertyName())
                    .description("미세먼지(PM10) Particulates")
                    .definition("https://en.wikipedia.org/wiki/Particulates")
                    .build());
        }

        ObservedProperty pm25 = sta.hasObservedProperty(null, AirQuality.PM25.getObservedPropertyName());
        if (pm25 == null) {
            // ObservedProperty PM2.5
            service.create(ObservedPropertyBuilder.builder()
                    .name(AirQuality.PM25.getObservedPropertyName())
                    .description("미세먼지(PM2.5) Particulates")
                    .definition("https://en.wikipedia.org/wiki/Particulates")
                    .build());
        }

        ObservedProperty so2 = sta.hasObservedProperty(null, AirQuality.SO2.getObservedPropertyName());
        if (so2 == null) {
            // ObservedProperty 아황산가스 농도
            service.create(ObservedPropertyBuilder.builder()
                    .name(AirQuality.SO2.getObservedPropertyName())
                    .description("아황산가스 농도 Sulfur_dioxide")
                    .definition("https://en.wikipedia.org/wiki/Sulfur_dioxide")
                    .build());
        }

        ObservedProperty co = sta.hasObservedProperty(null, AirQuality.CO.getObservedPropertyName());
        if (co == null) {
            // ObservedProperty 일산화탄소 농도
            service.create(ObservedPropertyBuilder.builder()
                    .name(AirQuality.CO.getObservedPropertyName())
                    .description("일산화탄소 농도 Carbon_monoxide")
                    .definition("https://en.wikipedia.org/wiki/Carbon_monoxide")
                    .build());
        }

        ObservedProperty o3 = sta.hasObservedProperty(null, AirQuality.O3.getObservedPropertyName());
        if (o3 == null) {
            // ObservedProperty 오존 농도
            service.create(ObservedPropertyBuilder.builder()
                    .name(AirQuality.O3.getObservedPropertyName())
                    .description("오존 농도 Ozone")
                    .definition("https://en.wikipedia.org/wiki/Ozone")
                    .build());
        }

        ObservedProperty no2 = sta.hasObservedProperty(null, AirQuality.NO2.getObservedPropertyName());
        if (no2 == null) {
            // ObservedProperty 이산화질소 농도
            service.create(ObservedPropertyBuilder.builder()
                    .name(AirQuality.NO2.getObservedPropertyName())
                    .description("이산화질소 Nitrogen_dioxide")
                    .definition("https://en.wikipedia.org/wiki/Nitrogen_dioxide")
                    .build());
        }

        ObservedPropertyExist = true;

        log.info("================== ObservedProperty insert success ==================");
    }

    /**
     * 측정소 목록 조회
     *
     * @return
     * @throws Exception
     */
    private JSONObject getListStation() throws Exception {
        boolean mockEnable = propertiesConfig.isMockEnable();
        JSONParser parser = new JSONParser();
        JSONObject stationJson;
        // 테스트
        if (mockEnable) {
            log.info("mock 미세먼지 저장소 목록");
            stationJson = (JSONObject) parser.parse(new FileReader(this.getClass().getClassLoader().getResource("sample/airQualityStation.json").getFile()));
        } else {
            // 운영시 api 연동
            log.info("api 연동 미세먼지 저장소 목록");
            String url = "http://openapi.airkorea.or.kr/openapi/services/rest/MsrstnInfoInqireSvc/getMsrstnList";
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            UriComponents builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("ServiceKey", "ZiKeHEKOV18foLQEgnvy1DHa%2FefMY%2F999Lk9MhSty%2FO9a0awuczi0DcG1X8x%2BhnMiNkileMj7w00M%2F0ZtKVfAw%3D%3D")
                    .queryParam("numOfRows", 10000)
                    .queryParam("pageNo", 1)
                    .queryParam("_returnType", "json")
                    .build(false);    //자동으로 encode해주는 것을 막기 위해 false
            // TODO ServiceKey 는 발급받은 키로 해야함. 개발용 api key 는 하루 request 500건으로 제한
            ResponseEntity<?> response = restTemplate.exchange(new URI(builder.toString()), HttpMethod.GET, entity, String.class);
            log.info("-------- statusCode = {}, body = {}", response.getStatusCodeValue(), response.getBody());

            stationJson = (JSONObject) parser.parse(response.getBody().toString());
        }

        return stationJson;
    }

    /**
     * 측정소에 해당하는 미세먼지 데이터 조회
     *
     * @param stationName
     * @return
     * @throws Exception
     */
    private JSONObject getAirQualityData(String stationName) throws Exception {
        boolean mockEnable = propertiesConfig.isMockEnable();
        JSONObject json = new JSONObject();
        // 테스트
        if (mockEnable) {
            Random random = new Random();
            // 미세먼지 pm10
            String pm10Value = String.valueOf(random.nextInt(601));
            String pm10Grade = getGrade(pm10Value, AirQuality.PM10);
            // 미세먼지 pm2.5
            String pm25Value = String.valueOf(random.nextInt(501));
            String pm25Grade = getGrade(pm25Value, AirQuality.PM25);
            // 아황산가스 농도
            String so2Value = String.valueOf(random.nextFloat());
            String so2Grade = getGrade(so2Value, AirQuality.SO2);
            // 일산화탄소 농도
            String coValue = String.valueOf(random.nextFloat() * 50);
            String coGrade = getGrade(coValue, AirQuality.CO);
            // 오존 농도
            String o3Value = String.valueOf(random.nextFloat() * 0.6);
            String o3Grade = getGrade(o3Value, AirQuality.O3);
            // 이산화질소 농도
            String no2Value = String.valueOf(random.nextFloat() * 2);
            String no2Grade = getGrade(no2Value, AirQuality.NO2);

            json.put("pm10Value", pm10Value);
            json.put("pm25Value", pm25Value);
            json.put("so2Value", so2Value);
            json.put("coValue", coValue);
            json.put("o3Value", o3Value);
            json.put("no2Value", no2Value);
            json.put("pm10Grade", pm10Grade);
            json.put("pm25Grade", pm25Grade);
            json.put("so2Grade", so2Grade);
            json.put("coGrade", coGrade);
            json.put("o3Grade", o3Grade);
            json.put("no2Grade", no2Grade);
            json.put("dataTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")));
        } else {
            // 운영시 api 연동
            String url = "http://openapi.airkorea.or.kr/openapi/services/rest/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty";
            JSONParser parser = new JSONParser();
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            UriComponents builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("ServiceKey", "ZiKeHEKOV18foLQEgnvy1DHa%2FefMY%2F999Lk9MhSty%2FO9a0awuczi0DcG1X8x%2BhnMiNkileMj7w00M%2F0ZtKVfAw%3D%3D")
                    .queryParam("numOfRows", 10000)
                    .queryParam("pageNo", 1)
                    .queryParam("stationName", stationName)
                    .queryParam("dataTerm", "DAILY")
                    .queryParam("ver", 1.3)
                    .queryParam("_returnType", "json")
                    .build(false);    //자동으로 encode해주는 것을 막기 위해 false
            // TODO ServiceKey 는 발급받은 키로 해야함. 개발용 api key 는 하루 request 500건으로 제한
            ResponseEntity<?> response = restTemplate.exchange(new URI(builder.toString()), HttpMethod.GET, entity, String.class);
            log.info("-------- statusCode = {}, body = {}", response.getStatusCodeValue(), response.getBody());

            JSONObject apiResultJson = (JSONObject) parser.parse(response.getBody().toString());
            List<?> resultList = (List<?>) apiResultJson.get("list");
            json = resultList.size() > 0 ? (JSONObject) resultList.get(0) : null;

        }

        return json;
    }

    /**
     * 미세먼지에 해당하는 모든 thing 의 정보들의 status false 로 업데이트
     */
    private void updateAirQualityThingsStatus() {
        boolean nextLinkCheck = true;
        int skipCount = 0;
        while (nextLinkCheck) {
            EntityList<Thing> things = null;
            try {
                things = service.things()
                        .query()
                        .skip(skipCount)
                        .filter("Datastreams/ObservedProperties/name eq " + "'" + AirQuality.PM10.getObservedPropertyName() + "'" +
                                " or name eq " + "'" + AirQuality.PM25.getObservedPropertyName() + "'" +
                                " or name eq " + "'" + AirQuality.SO2.getObservedPropertyName() + "'" +
                                " or name eq " + "'" + AirQuality.CO.getObservedPropertyName() + "'" +
                                " or name eq " + "'" + AirQuality.O3.getObservedPropertyName() + "'" +
                                " or name eq " + "'" + AirQuality.NO2.getObservedPropertyName() + "'"
                        )
                        .list();

                for (var thing : things) {
                    var properties = thing.getProperties();
                    properties.put("available", false);
                    thing.setProperties(properties);
                    service.update(thing);
                }
            } catch (ServiceFailureException e) {
                LogMessageSupport.printMessage(e, "-------- AirQualityService updateAirQualityThingsStatus Error = {}", e.getMessage());
            }
            nextLinkCheck = things.getNextLink() != null;
            skipCount = skipCount + 100;
        }
    }

    /**
     * 에어코리아 기준에 해당하는 grade return
     *
     * @param value 측정데이터 값
     * @param type  측정데이터 타입
     * @return
     */
    private String getGrade(String value, AirQuality type) {
        String grade = "";
        float floatNum = Float.parseFloat(value);
        if (AirQuality.SO2 == type) {
            if (floatNum >= 0 && floatNum <= 0.02) {
                grade = "1";
            } else if (floatNum >= 0.021 && floatNum <= 0.05) {
                grade = "2";
            } else if (floatNum >= 0.051 && floatNum <= 0.15) {
                grade = "3";
            } else if (floatNum >= 0.151 && floatNum <= 1) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if (AirQuality.CO == type) {
            if (0 >= floatNum && floatNum <= 2) {
                grade = "1";
            } else if (floatNum >= 2.01 && floatNum <= 9) {
                grade = "2";
            } else if (floatNum >= 9.01 && floatNum <= 15) {
                grade = "3";
            } else if (floatNum >= 15.01 && floatNum <= 50) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if (AirQuality.O3 == type) {
            if (0 >= floatNum && floatNum <= 0.03) {
                grade = "1";
            } else if (floatNum >= 0.031 && floatNum <= 0.09) {
                grade = "2";
            } else if (floatNum >= 0.091 && floatNum <= 0.15) {
                grade = "3";
            } else if (floatNum >= 0.151 && floatNum <= 0.6) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if (AirQuality.NO2 == type) {
            if (floatNum >= 0 && floatNum <= 0.03) {
                grade = "1";
            } else if (floatNum >= 0.031 && floatNum <= 0.06) {
                grade = "2";
            } else if (floatNum >= 0.061 && floatNum <= 0.2) {
                grade = "3";
            } else if (floatNum >= 0.201 && floatNum <= 2) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if (AirQuality.PM10 == type) {
            int intNum = Integer.parseInt(value);
            if (intNum >= 0 && intNum <= 30) {
                grade = "1";
            } else if (intNum >= 31 & intNum <= 80) {
                grade = "2";
            } else if (intNum >= 81 && intNum <= 150) {
                grade = "3";
            } else if (intNum >= 151 && intNum <= 600) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if (AirQuality.PM25 == type) {
            int intNum = Integer.parseInt(value);
            if (intNum >= 0 && intNum <= 15) {
                grade = "1";
            } else if (intNum >= 16 && intNum <= 35) {
                grade = "2";
            } else if (intNum >= 36 && intNum <= 75) {
                grade = "3";
            } else if (intNum >= 76 && intNum <= 500) {
                grade = "4";
            } else {
                grade = "1";
            }
        }

        return grade;
    }
}
