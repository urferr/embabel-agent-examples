package com.embabel.example.horoscope;

import java.net.http.HttpClient;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class HoroscopeAppApiHoroscopeService implements HoroscopeService {
	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	HoroscopeAppApiHoroscopeService(RestClient.Builder theRestClientBuilder) {
		restClient = theRestClientBuilder
				.baseUrl("https://horoscope-app-api.vercel.app")
				.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
				.build();
	}

	@Override
	public String dailyHoroscope(String theSign) {
		var body = restClient.get()
				.uri("/api/v1/get-horoscope/daily?sign={sign}&day=today", theSign.toLowerCase())
				.retrieve()
				.body(String.class);

		if (body != null) {
			HoroscopeResponse response;
			try {
				response = objectMapper.readValue(body, HoroscopeResponse.class);
				
				if ( response.data != null && response.data.horoscope != null) {
					return response.data.horoscope;
				}
			}
			catch (JsonProcessingException theCause) {
			}
		}
		return "Unable to retrieve horoscope for $sign today.";
	}

	private static record HoroscopeResponse(HoroscopeData data) {
	}

	private static record HoroscopeData(String date, String sign, String period, String horoscope) {
	}
}
