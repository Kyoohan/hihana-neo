import XCTest
@testable import HiHanaEvo

/// 날씨 상세 — 안드로이드 14.3 WeatherScreen(체감온도·습도·바람 타일, 강수량·적설 표기)과 같은 기준인지.
final class WeatherTests: XCTestCase {
    func testFeelsNote() {
        XCTAssertEqual(Weather.feelsNote(feels: 1, temp: 4.4), "실제보다 3° 낮아요")
        XCTAssertEqual(Weather.feelsNote(feels: 31.6, temp: 29.2), "실제보다 3° 높아요")
        XCTAssertEqual(Weather.feelsNote(feels: 4.2, temp: 4.4), "실제 기온과 비슷해요")
        // 화면에 보이는 반올림 값끼리 비교 — 4.6°(→5°)와 4.4°(→4°)는 1° 차이.
        XCTAssertEqual(Weather.feelsNote(feels: 4.6, temp: 4.4), "실제보다 1° 높아요")
        XCTAssertEqual(Weather.feelsNote(feels: -3, temp: nil), "실제 기온과 비슷해요")
    }

    func testHumidityNote() {
        XCTAssertEqual(Weather.humidityNote(20), "건조해요")
        XCTAssertEqual(Weather.humidityNote(35), "건조해요")
        XCTAssertEqual(Weather.humidityNote(36), "적당해요")
        XCTAssertEqual(Weather.humidityNote(60), "적당해요")
        XCTAssertEqual(Weather.humidityNote(61), "습해요")
        XCTAssertEqual(Weather.humidityNote(80), "습해요")
        XCTAssertEqual(Weather.humidityNote(81), "매우 습해요")
    }

    func testWindStrength() {
        XCTAssertEqual(Weather.windStrength(0), "거의 없어요")
        XCTAssertEqual(Weather.windStrength(0.49), "거의 없어요")
        XCTAssertEqual(Weather.windStrength(0.5), "약한 바람")
        XCTAssertEqual(Weather.windStrength(3.9), "약한 바람")
        XCTAssertEqual(Weather.windStrength(4), "약간 강한 바람")
        XCTAssertEqual(Weather.windStrength(9), "강한 바람")
        XCTAssertEqual(Weather.windStrength(13.9), "강한 바람")
        XCTAssertEqual(Weather.windStrength(14), "매우 강한 바람")
    }

    func testWindFrom() {
        XCTAssertEqual(Weather.windFrom(0), "북풍")
        XCTAssertEqual(Weather.windFrom(22), "북풍")
        XCTAssertEqual(Weather.windFrom(23), "북동풍")
        XCTAssertEqual(Weather.windFrom(90), "동풍")
        XCTAssertEqual(Weather.windFrom(180), "남풍")
        XCTAssertEqual(Weather.windFrom(266), "서풍")
        // 북 구간은 337.5°~22.5° — 338°부터 북풍, 337°까지 북서풍.
        XCTAssertEqual(Weather.windFrom(339), "북풍")
        XCTAssertEqual(Weather.windFrom(338), "북풍")
        XCTAssertEqual(Weather.windFrom(337), "북서풍")
        XCTAssertEqual(Weather.windFrom(315), "북서풍")
        XCTAssertEqual(Weather.windFrom(360), "북풍")
        XCTAssertEqual(Weather.windFrom(-90), "서풍")
    }

    func testWindNote() {
        XCTAssertEqual(Weather.windNote(speed: 4.1, dir: 266), "서풍 · 약간 강한 바람")
        XCTAssertEqual(Weather.windNote(speed: 0.3, dir: 266), "거의 없어요")
        XCTAssertEqual(Weather.windNote(speed: 2, dir: nil), "약한 바람")
        XCTAssertEqual(Weather.speed(4.1), "4.1m/s")
        XCTAssertEqual(Weather.speed(4), "4m/s")
    }

    func testAmounts() {
        XCTAssertEqual(Weather.mm(0.5), "1mm 미만")
        XCTAssertEqual(Weather.mm(1), "1mm")
        XCTAssertEqual(Weather.mm(2.35), "2.4mm")
        XCTAssertEqual(Weather.mm(30), "30mm")
        XCTAssertEqual(Weather.cm(0.5), "1cm 미만")
        XCTAssertEqual(Weather.cm(1.5), "1.5cm")
    }

    /// 실제 서버 응답(2026-11-19 14시 서울, 줄임)을 그대로 읽는지.
    func testDecodeLiveShape() throws {
        let json = """
        {"place":"seoul","source":"kma",
         "current":{"time":"2026-11-19T14:00","temp":4.4,"code":0,"rain":null,"feels":1,"humidity":39,"wind":4.1,"windDir":266},
         "hourly":[{"t":"2026-11-19T14:00","temp":4,"code":0,"pop":0,"rain":null,"rainText":null,"snow":null,"snowText":null},
                   {"t":"2026-11-19T16:00","temp":4,"code":77,"pop":40,"rain":0.5,"rainText":"1mm 미만","snow":null,"snowText":null}],
         "daily":[{"date":"2026-11-19","code":77,"max":4,"min":-1,"pop":40,"rain":0.5,"snow":null}],
         "updated":"2026-11-19T05:16:46.992Z"}
        """
        let f = try XCTUnwrap(Weather.decode(Data(json.utf8)))
        XCTAssertEqual(f.currentFeels, 1)
        XCTAssertEqual(f.currentHumidity, 39)
        XCTAssertEqual(f.currentWind, 4.1)
        XCTAssertEqual(f.currentWindDir, 266)
        XCTAssertNil(f.currentRain)
        XCTAssertNil(f.hours[0].rainText)
        XCTAssertEqual(f.hours[1].rainText, "1mm 미만")
        XCTAssertNil(f.hours[1].snowText)
        XCTAssertEqual(f.days[0].rain, 0.5)
        XCTAssertNil(f.days[0].snow)
    }

    /// 지금 비가 0mm 로 오면(실황 RN1 = 0) 지난 1시간 줄을 두지 않습니다.
    func testZeroCurrentRainIsNil() throws {
        let json = #"{"current":{"temp":3,"code":61,"rain":0},"hourly":[],"daily":[]}"#
        XCTAssertNil(try XCTUnwrap(Weather.decode(Data(json.utf8))).currentRain)
        let wet = #"{"current":{"temp":3,"code":61,"rain":1.5},"hourly":[],"daily":[]}"#
        XCTAssertEqual(try XCTUnwrap(Weather.decode(Data(wet.utf8))).currentRain, 1.5)
    }

    /// 새 칸이 없던 예전 캐시(JSONCache)도 그대로 읽혀야 합니다 — 앱을 올려도 날씨가 비지 않게.
    func testOldCacheStillDecodes() throws {
        let old = #"{"currentTemp":4.4,"currentCode":0,"hours":[{"time":0,"temp":4,"code":0,"pop":0}],"days":[{"date":{"year":2026,"month":11,"day":19},"code":0,"max":4,"min":-1,"pop":0}],"source":"kma"}"#
        let f = try JSONDecoder().decode(Weather.Forecast.self, from: Data(old.utf8))
        XCTAssertEqual(f.currentTemp, 4.4)
        XCTAssertNil(f.currentFeels)
        XCTAssertNil(f.hours[0].rainText)
        XCTAssertNil(f.days[0].rain)
    }
}
