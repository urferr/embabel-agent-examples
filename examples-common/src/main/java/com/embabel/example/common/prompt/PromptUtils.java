package com.embabel.example.common.prompt;

import com.embabel.common.util.DummyInstanceCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PromptUtils {
	private static final DummyInstanceCreator dummyInstanceCreator = new DummyInstanceCreator();

    private static ObjectMapper om = new ObjectMapper();

    /**
     * Generates a JSON example of the given class
     * with dummy data. Makes few shot examples easier to create.
     *
     * @param clazz The class to generate a JSON example for.
     * @throws JsonProcessingException 
     */
    public static String jsonExampleOf(Class<?> clazz) {
        var dummy = dummyInstanceCreator.createDummyInstance(clazz);
        try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(dummy);
		}
		catch (JsonProcessingException theCause) {
			throw new RuntimeException(theCause);
		}
    }
}
