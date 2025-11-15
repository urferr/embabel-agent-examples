package com.embabel.example.horoscope;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class HoroscopeAppApiHoroscopeService implements HoroscopeService {
	private final RestClient restClient;

	HoroscopeAppApiHoroscopeService(RestClient.Builder theRestClientBuilder) {
		restClient = theRestClientBuilder.baseUrl("https://horoscope-app-api.vercel.app").build();
	}

	@Override
	public String dailyHoroscope(String theSign) {
		var response = restClient.get().uri("/api/v1/get-horoscope/daily?sign={sign}", theSign.toLowerCase()).retrieve()
				.body(HoroscopeResponse.class);

		if (response != null && response.data != null && response.data.horoscope_data != null) {
			return response.data.horoscope_data;
		}
		return "Unable to retrieve horoscope for $sign today.";
	}

	private static record HoroscopeResponse(Boolean success, Integer status, HoroscopeData data) {
	}

	private static record HoroscopeData(String date, String horoscope_data) {
	}
}
