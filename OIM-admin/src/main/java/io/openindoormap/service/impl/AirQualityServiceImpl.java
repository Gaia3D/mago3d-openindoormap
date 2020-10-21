package io.openindoormap.service.impl;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.*;
import de.fraunhofer.iosb.ilt.sta.model.builder.*;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractDatastreamBuilder;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractFeatureOfInterestBuilder;
import de.fraunhofer.iosb.ilt.sta.model.builder.api.AbstractLocationBuilder;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import io.openindoormap.config.PropertiesConfig;
import io.openindoormap.domain.Sensor.AirQuality;
import io.openindoormap.service.AirQualityService;
import io.openindoormap.support.LogMessageSupport;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geojson.Point;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.FileReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * sensor 초기 데이터 생성 및 갱신
 */
@Service("airQualityService")
@AllArgsConstructor
@Slf4j
public class AirQualityServiceImpl implements AirQualityService {

    private final SensorThingsService sensorThingsService;
    private final PropertiesConfig propertiesConfig;
    private final JSONParser parser;

    @Override
    public void initSensorData() {
        // 기존 데이터가 있고 저장소 목록에 변동사항이 없다면 return
        if(initDataExistCheck()) {
            log.info("============================ init data Exist ============================");
            return;
        } else {
            // TODO : 기존 데이터 삭제 필요
        }

        JSONObject stationJson = null;
        try {
            stationJson = getListStation();
        } catch (Exception e) {
            LogMessageSupport.printMessage(e, "-------- AirQualityService Error = {}", e.getMessage());
        }
        // 저장소 목록
        List<?> stationList = (List<?>) stationJson.get("list");
        int id = getThingsId("desc");
        try {
            // ObservedProperty PM10
            sensorThingsService.create(ObservedPropertyBuilder.builder()
                    .name("pm10Value")
                    .description("미세먼지(PM10) Particulates")
                    .definition("https://en.wikipedia.org/wiki/Particulates")
                    .build());

            // ObservedProperty PM2.5
            sensorThingsService.create(ObservedPropertyBuilder.builder()
                    .name("pm25Value")
                    .description("미세먼지(PM2.5) Particulates")
                    .definition("https://en.wikipedia.org/wiki/Particulates")
                    .build());

            // ObservedProperty 아황산가스 농도
            sensorThingsService.create(ObservedPropertyBuilder.builder()
                    .name("so2Value")
                    .description("아황산가스 농도 Sulfur_dioxide")
                    .definition("https://en.wikipedia.org/wiki/Sulfur_dioxide")
                    .build());

            // ObservedProperty 일산화탄소 농도
            sensorThingsService.create(ObservedPropertyBuilder.builder()
                    .name("coValue")
                    .description("일산화탄소 농도 Carbon_monoxide")
                    .definition("https://en.wikipedia.org/wiki/Carbon_monoxide")
                    .build());

            // ObservedProperty 오존 농도
            sensorThingsService.create(ObservedPropertyBuilder.builder()
                    .name("o3Value")
                    .description("오존 농도 Ozone")
                    .definition("https://en.wikipedia.org/wiki/Ozone")
                    .build());

            // ObservedProperty 이산화질소 농도
            sensorThingsService.create(ObservedPropertyBuilder.builder()
                    .name("no2Value")
                    .description("이산화질소 Nitrogen_dioxide")
                    .definition("https://en.wikipedia.org/wiki/Nitrogen_dioxide")
                    .build());

            for (var station : stationList) {
                var json = (JSONObject) station;
                var stationName = (String) json.get("stationName");
                var dmX = json.get("dmX").toString().trim();
                var dmY = json.get("dmY").toString().trim();
                // 위치 정보가 없는 측정소의 경우 1,1 로 좌표 넣어줌
                var point = "".equals(dmX) || "".equals(dmY) ? new Point(1, 1) : new Point(Double.parseDouble(dmY), Double.parseDouble(dmX));
                ++id;

                // Thing
                Map<String, Object> thingProperties = new HashMap<>();
                thingProperties.put("stationName", stationName);
                thingProperties.put("year", json.get("year"));
                thingProperties.put("oper", json.get("oper"));
                thingProperties.put("photo", json.get("photo"));
                thingProperties.put("vrml", json.get("vrml"));
                thingProperties.put("map", json.get("map"));
                thingProperties.put("mangName", json.get("mangName"));
                thingProperties.put("item", json.get("item"));

                sensorThingsService.create(ThingBuilder.builder()
                        .name(stationName)
                        .description("한국환경공단 측정소")
                        .properties(thingProperties)
                        .build());

                // Location
                sensorThingsService.create(LocationBuilder.builder()
                        .name((String) json.get("addr"))
                        .encodingType(AbstractLocationBuilder.ValueCode.GeoJSON)
                        .description("대기질 측정소 위치")
                        .location(point)
                        .build());

                // DataStream PM10
                sensorThingsService.create(DatastreamBuilder.builder()
                        .name("미세먼지(PM10)")
                        .description("미세먼지(PM10)")
                        .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                        .unitOfMeasurement(new UnitOfMeasurement(
                                "microgram per cubic meter",
                                "㎍/m³",
                                "https://www.eea.europa.eu/themes/air/air-quality/resources/glossary/g-m3"
                        ))
                        .sensor(new Sensor(
                                stationName + ":" + "미세먼지(PM10)",
                                "미세먼지 측정소",
                                "http://schema.org/description",
                                json.get("mangName")
                        ))
                        .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse("1")).build())
                        .thing(ThingBuilder.builder().id(Id.tryToParse(String.valueOf(id))).build())
                        .build());

                // DataStream PM2.5
                sensorThingsService.create(DatastreamBuilder.builder()
                        .name("미세먼지(PM2.5)")
                        .description("미세먼지(PM2.5)")
                        .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                        .unitOfMeasurement(new UnitOfMeasurement(
                                "microgram per cubic meter",
                                "㎍/m³",
                                "https://www.eea.europa.eu/themes/air/air-quality/resources/glossary/g-m3"
                        ))
                        .sensor(new Sensor(
                                stationName + ":" + "미세먼지(PM2.5)",
                                "미세먼지 측정소",
                                "http://schema.org/description",
                                json.get("mangName")
                        ))
                        .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse("2")).build())
                        .thing(ThingBuilder.builder().id(Id.tryToParse(String.valueOf(id))).build())
                        .build());

                // DataStream 아황산가스 농도
                sensorThingsService.create(DatastreamBuilder.builder()
                        .name("아황산가스 농도")
                        .description("아황산가스 농도")
                        .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                        .unitOfMeasurement(new UnitOfMeasurement(
                                "parts per million",
                                "ppm",
                                "https://en.wikipedia.org/wiki/Parts-per_notation"
                        ))
                        .sensor(new Sensor(
                                stationName + ":" + "아황산가스 농도",
                                "아황산가스 측정소",
                                "http://schema.org/description",
                                json.get("mangName")
                        ))
                        .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse("3")).build())
                        .thing(ThingBuilder.builder().id(Id.tryToParse(String.valueOf(id))).build())
                        .build());

                // DataStream 일산화탄소 농도
                sensorThingsService.create(DatastreamBuilder.builder()
                        .name("일산화탄소 농도")
                        .description("일산화탄소 농도")
                        .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                        .unitOfMeasurement(new UnitOfMeasurement(
                                "parts per million",
                                "ppm",
                                "https://en.wikipedia.org/wiki/Parts-per_notation"
                        ))
                        .sensor(new Sensor(
                                stationName + ":" + "일산화탄소 농도",
                                "일산화탄소 측정소",
                                "http://schema.org/description",
                                json.get("mangName")
                        ))
                        .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse("4")).build())
                        .thing(ThingBuilder.builder().id(Id.tryToParse(String.valueOf(id))).build())
                        .build());

                // DataStream 오존 농도
                sensorThingsService.create(DatastreamBuilder.builder()
                        .name("오존 농도")
                        .description("오존 농도")
                        .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                        .unitOfMeasurement(new UnitOfMeasurement(
                                "parts per million",
                                "ppm",
                                "https://en.wikipedia.org/wiki/Parts-per_notation"
                        ))
                        .sensor(new Sensor(
                                stationName + ":" + "오존 농도",
                                "오존 측정소",
                                "http://schema.org/description",
                                json.get("mangName")
                        ))
                        .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse("5")).build())
                        .thing(ThingBuilder.builder().id(Id.tryToParse(String.valueOf(id))).build())
                        .build());

                // DataStream 이산화질소 농도
                sensorThingsService.create(DatastreamBuilder.builder()
                        .name("이산화질소 농도")
                        .description("이산화질소 농도")
                        .observationType(AbstractDatastreamBuilder.ValueCode.OM_Observation)
                        .unitOfMeasurement(new UnitOfMeasurement(
                                "parts per million",
                                "ppm",
                                "https://en.wikipedia.org/wiki/Parts-per_notation"
                        ))
                        .sensor(new Sensor(
                                stationName + ":" + "이산화질소 농도",
                                "이산화질소 측정소",
                                "http://schema.org/description",
                                json.get("mangName")
                        ))
                        .observedProperty(ObservedPropertyBuilder.builder().id(Id.tryToParse("6")).build())
                        .thing(ThingBuilder.builder().id(Id.tryToParse(String.valueOf(id))).build())
                        .build());

                // FeatureOfInterest
                sensorThingsService.create(FeatureOfInterestBuilder.builder()
                        .name(stationName + " 측정소")
                        .description("한국환경공단 대기질 측정소")
                        .encodingType(AbstractFeatureOfInterestBuilder.ValueCode.GeoJSON)
                        .feature(point)
                        .build());
            }
        } catch (ServiceFailureException e) {
            LogMessageSupport.printMessage(e, "-------- AirQualityService ServiceFailureException = {}", e.getMessage());
        }
    }

    @Override
    public void insertSensorData() {
        JSONObject stationJson = null;
        try {
            stationJson = getListStation();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<?> stationList = (List<?>) stationJson.get("list");
        for(var station : stationList) {
            var jsonObject = (JSONObject) station;
            var stationName = (String)jsonObject.get("stationName");
            JSONObject json = new JSONObject();
            try {
                json.clear();
                JSONObject result = getAirQualityData(stationName);
                LocalDateTime t = LocalDateTime.parse((String)result.get("dataTime"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                ZonedDateTime zonedDateTime = ZonedDateTime.of(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), 0, 0, 0, ZoneId.of("Asia/Seoul"));
                EntityList<Thing> things = sensorThingsService.things()
                        .query()
                        .filter("name eq " + "'"+ stationName +"'")
                        .expand("Datastreams($orderby=id asc)")
                        .list();
                Thing thing = things.toList().get(0);
                EntityList<Datastream> datastreamList = thing.getDatastreams();
                for(var datastream : datastreamList) {
                    String name = datastream.getName();
                    if(name.equals(AirQuality.PM10.getValue())) {
                        json.put("value", result.get("pm10Value"));
                        json.put("grade", result.get("pm10Grade"));
                    } else if(name.equals(AirQuality.PM25.getValue())) {
                        json.put("value", result.get("pm25Value"));
                        json.put("grade", result.get("pm25Grade"));
                    } else if(name.equals(AirQuality.SO2.getValue())) {
                        json.put("value", result.get("so2Value"));
                        json.put("grade", result.get("so2Grade"));
                    } else if(name.equals(AirQuality.CO.getValue())) {
                        json.put("value", result.get("coValue"));
                        json.put("grade", result.get("coGrade"));
                    } else if(name.equals(AirQuality.O3.getValue())) {
                        json.put("value", result.get("o3Value"));
                        json.put("grade", result.get("o3Grade"));
                    } else if(name.equals(AirQuality.NO2.getValue())) {
                        json.put("value", result.get("no2Value"));
                        json.put("grade", result.get("no2Grade"));
                    }
                    sensorThingsService.create(ObservationBuilder.builder()
                            .phenomenonTime(new TimeObject(ZonedDateTime.now()))
                            .resultTime(zonedDateTime)
                            .result(json)
                            .datastream(DatastreamBuilder.builder().id(Id.tryToParse(String.valueOf(datastream.getId()))).build())
                            .featureOfInterest(FeatureOfInterestBuilder.builder().id(Id.tryToParse(String.valueOf(thing.getId()))).build())
                            .build());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void initMonthMockData() {
        JSONObject stationJson = null;
        try {
            stationJson = getListStation();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<?> stationList = (List<?>) stationJson.get("list");
        for(var station : stationList) {
            var jsonObject = (JSONObject) station;
            var stationName = (String)jsonObject.get("stationName");
            JSONObject json = new JSONObject();
            try {
                json.clear();
                for(int i=0; i< 24;i++) {
                    JSONObject result = getAirQualityData(stationName);
                    LocalDateTime t = LocalDateTime.parse((String)result.get("dataTime"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    t = t.minusDays(1);
                    t = t.plusHours(i);
                    ZonedDateTime zonedDateTime = ZonedDateTime.of(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), 0, 0, 0, ZoneId.of("Asia/Seoul"));
                    EntityList<Thing> things = sensorThingsService.things()
                            .query()
                            .filter("name eq " + "'"+ stationName +"'")
                            .expand("Datastreams($orderby=id asc)")
                            .list();
                    Thing thing = things.toList().get(0);
                    EntityList<Datastream> datastreamList = thing.getDatastreams();
                    for(var datastream : datastreamList) {
                        String name = datastream.getName();
                        if(name.equals(AirQuality.PM10.getValue())) {
                            json.put("value", result.get("pm10Value"));
                            json.put("grade", result.get("pm10Grade"));
                        } else if(name.equals(AirQuality.PM25.getValue())) {
                            json.put("value", result.get("pm25Value"));
                            json.put("grade", result.get("pm25Grade"));
                        } else if(name.equals(AirQuality.SO2.getValue())) {
                            json.put("value", result.get("so2Value"));
                            json.put("grade", result.get("so2Grade"));
                        } else if(name.equals(AirQuality.CO.getValue())) {
                            json.put("value", result.get("coValue"));
                            json.put("grade", result.get("coGrade"));
                        } else if(name.equals(AirQuality.O3.getValue())) {
                            json.put("value", result.get("o3Value"));
                            json.put("grade", result.get("o3Grade"));
                        } else if(name.equals(AirQuality.NO2.getValue())) {
                            json.put("value", result.get("no2Value"));
                            json.put("grade", result.get("no2Grade"));
                        }
                        sensorThingsService.create(ObservationBuilder.builder()
                                .phenomenonTime(new TimeObject(ZonedDateTime.now()))
                                .resultTime(zonedDateTime)
                                .result(json)
                                .datastream(DatastreamBuilder.builder().id(Id.tryToParse(String.valueOf(datastream.getId()))).build())
                                .featureOfInterest(FeatureOfInterestBuilder.builder().id(Id.tryToParse(String.valueOf(thing.getId()))).build())
                                .build());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 측정소 목록 조회
     * @return
     * @throws Exception
     */
    private JSONObject getListStation() throws Exception {
        boolean mockEnable = propertiesConfig.isMockEnable();
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
                    .queryParam("ServiceKey", "4EA8xQz4hBCUI0azTs4P6Xznia8j5fjbeA%2F33IADvvdxt2MkVGsjVzU4yjn2tjyrjkww73GoOncpjz5L4nKdvg%3D%3D")
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
            String o3Value = String.valueOf(random.nextFloat()*0.6);
            String o3Grade = getGrade(o3Value, AirQuality.O3);
            // 이산화질소 농도
            String no2Value = String.valueOf(random.nextFloat() * 2);
            String no2Grade = getGrade(no2Value, AirQuality.NO2);

            json.put("pm10Value", pm10Value);
            json.put("pm25Value", pm25Grade);
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
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            UriComponents builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("ServiceKey", "4EA8xQz4hBCUI0azTs4P6Xznia8j5fjbeA%2F33IADvvdxt2MkVGsjVzU4yjn2tjyrjkww73GoOncpjz5L4nKdvg%3D%3D")
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
     * 초기 데이터 존재하는지 체크
     * @return
     */
    private boolean initDataExistCheck() {
        boolean dataExistFlag = false;
        String AIR_QUALITY_THINGS_FILER = "'한국환경공단 측정소'";
        try {
            JSONObject stationJson = getListStation();
            List<?> stationList = (List<?>) stationJson.get("list");
            EntityList<Thing> things = sensorThingsService.things()
                    .query()
                    .filter("description eq " + AIR_QUALITY_THINGS_FILER)
                    .orderBy("id desc")
                    .top(1)
                    .list();

            List<Thing> thingList = things.toList();
            int idCount = thingList.size() > 0 ? Integer.parseInt(thingList.get(0).getId().toString()) : 0;
            int stationCount = stationList.size();
            dataExistFlag = idCount > 0 && idCount == stationCount;
        } catch (Exception e) {
            LogMessageSupport.printMessage(e, "-------- AirQualityService ServiceFailureException = {}", e.getMessage());
        }

        return dataExistFlag;
    }

    private int getThingsId(String orderBy) {
        int idCount = 0;
        try {
            EntityList<Thing> things = sensorThingsService.things()
                    .query()
                    .orderBy("id " + orderBy)
                    .top(1)
                    .list();

            List<Thing> thingList = things.toList();
            idCount = thingList.size() > 0 ? Integer.parseInt(thingList.get(0).getId().toString()) : 0;
        } catch (Exception e) {
            LogMessageSupport.printMessage(e, "-------- AirQualityService ServiceFailureException = {}", e.getMessage());
        }

        return idCount;
    }

    private String getGrade(String value, AirQuality type) {
        String grade = "";
        float floatNum = Float.parseFloat(value);
        if(AirQuality.SO2 == type ) {
            if(0 >= floatNum && floatNum <= 0.02) {
                grade = "1";
            } else if(0.021 >= floatNum && floatNum <= 0.05) {
                grade = "2";
            } else if(0.051 >= floatNum && floatNum <= 0.15) {
                grade = "3";
            } else if(0.151 >= floatNum && floatNum <= 1) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if(AirQuality.CO == type ) {
            if(0 >= floatNum && floatNum <= 2) {
                grade = "1";
            } else if(2.01 >= floatNum && floatNum <= 9) {
                grade = "2";
            } else if(9.01 >= floatNum && floatNum <= 15) {
                grade = "3";
            } else if(15.01 >= floatNum && floatNum <= 50) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if(AirQuality.O3 == type ) {
            if(0 >= floatNum && floatNum <= 0.03) {
                grade = "1";
            } else if(0.031 >= floatNum && floatNum <= 0.09) {
                grade = "2";
            } else if(0.091 >= floatNum && floatNum <= 0.15) {
                grade = "3";
            } else if(0.151 >= floatNum && floatNum <= 0.6) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if(AirQuality.NO2 == type ) {
            if(0 >= floatNum && floatNum <= 0.03) {
                grade = "1";
            } else if(0.031 >= floatNum && floatNum <= 0.06) {
                grade = "2";
            } else if(0.061 >= floatNum && floatNum <= 0.2) {
                grade = "3";
            } else if(0.201 >= floatNum && floatNum <= 2) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if(AirQuality.PM10 == type ) {
            float intNum = Integer.parseInt(value);
            if(0 >= intNum && intNum <= 30) {
                grade = "1";
            } else if(31 >= intNum && intNum <= 80) {
                grade = "2";
            } else if(81 >= intNum && intNum <= 150) {
                grade = "3";
            } else if(151 >= intNum && intNum <= 600) {
                grade = "4";
            } else {
                grade = "1";
            }
        } else if(AirQuality.PM25 == type ) {
            float intNum = Integer.parseInt(value);
            if(0 >= intNum && intNum <= 15) {
                grade = "1";
            } else if(16 >= intNum && intNum <= 35) {
                grade = "2";
            } else if(36 >= intNum && intNum <= 75) {
                grade = "3";
            } else if(76 >= intNum && intNum <= 500) {
                grade = "4";
            } else {
                grade = "1";
            }
        }

        return grade;
    }
}
