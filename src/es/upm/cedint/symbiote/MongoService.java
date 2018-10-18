package es.upm.cedint.symbiote;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;

import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class MongoService {
	
	static final String CONSUMER_KEY = "osxCqMeN6uM5QP7pwqckmzXsH";
	static final String CONSUMER_SECRET = "B5DwbrWeGL2sIE4ORSd1WzGDwc3I0wW7TEaqpKNhVQHGhtgLHO";
	static final String ACCESS_TOKEN = "1052619669910183936-p3Y4DfuY2rqdbANHNpGAhTUOi8RAjL";
	static final String ACCESS_TOKEN_SECRET = "Vb4zviKE74vzJExEI4hRcFD5PBS1Cko7f3k6iTLagrOTq";

	public static Twitter getTwitterInstance() {
	    ConfigurationBuilder cb = new ConfigurationBuilder();
	    cb.setDebugEnabled(true)
	    .setOAuthConsumerKey(CONSUMER_KEY)
	    .setOAuthConsumerSecret(CONSUMER_SECRET)
	    .setOAuthAccessToken(ACCESS_TOKEN)
	    .setOAuthAccessTokenSecret(ACCESS_TOKEN_SECRET);

	    TwitterFactory tf = new TwitterFactory(cb.build());
	    return tf.getInstance();
	}
	
	private static void updateTweet(Twitter twitter, String tweet) throws TwitterException {
	    Status status = twitter.updateStatus(tweet);
	    System.out.println("Successfully updated the status to [" + status.getText() + "].");
	}
	
	public JSONArray createLocations(double latitudeMIN, double longitudeMIN, double latitudeMAX, double longitudeMAX) {
		double mult = 1.5;
		double lat = 0.003;
		double lng = lat*mult;
		double latitude = latitudeMIN;
		double longitude = longitudeMIN;
		JSONArray jsonArray = new JSONArray();
		while (latitude <= latitudeMAX ) {
			while (longitude <= longitudeMAX)
			{
				JSONObject jsonObject = new JSONObject("{ \"latitude\": \""+latitude+"\", \"longitude\": \""+longitude+"\" }");
				jsonArray.put(jsonObject);
				longitude += 2*lng;
			}
			longitude = longitudeMIN;
			latitude += 2*lat;
		}
		return jsonArray;
	}

	public void insertFeatureCollection(JSONArray locations) {

		String location = "Zagreb";

		System.out.println(locations.toString());

		URL url;
		HttpURLConnection urlConnection = null;
		JSONArray jsonArray = new JSONArray();

		try {
			url = new URL("http://smeur.tel.fer.hr:8823/smeur/interpolation");
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setDoInput(true);
			urlConnection.setRequestProperty("Content-Type", "application/json");
			urlConnection.setRequestProperty("Accept", "application/json");
			urlConnection.setRequestMethod("POST");
			OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream());
			wr.write(locations.toString());
			wr.flush();
			InputStream in = new BufferedInputStream(urlConnection.getInputStream());
			BufferedReader buffered = new BufferedReader(new InputStreamReader(in));
			String line = "";
			StringBuilder builder = new StringBuilder();
			while((line = buffered.readLine()) != null)
				builder.append(line);
			in.close();
			jsonArray = new JSONArray(builder.toString());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		JSONArray observations = jsonArray;
		boolean tweet = false;
		String tweet_locations = "";
		long time = Calendar.getInstance().getTimeInMillis();

		JSONObject observationsData = new JSONObject("{\"time\":"+time+",\"location\":"+location+",\"geojson\": {\"type\":\"FeatureCollection\",\"features\":[]}}");
		for (int i = 0; i < observations.length(); i++) {
			double mult = 1.5;
			double lat = 0.003;
			double lng = lat*mult;
			JSONObject object = observations.getJSONObject(i);
			if (object != null && object.getJSONArray("observation") != null) {
				JSONArray parameters = object.getJSONArray("observation");
				ArrayList<Float> values = new ArrayList<>();
				for (int j = 0; j < parameters.length(); j++) {
					JSONObject parameter = parameters.getJSONObject(j);
					// El maximo AQI
					if (parameter.getJSONObject("obsProperty").getString("name").contains("AQI")) {
						float value = Float.parseFloat(parameter.getString("value"));
						values.add(value);
					}
				}
				int largest = 0;
				for (int j = 1; j < values.size(); j++) {
					if ( values.get(j) > values.get(largest) ) largest = j;
				}
				float value = values.get(largest);
				if (value >= 100) {
					tweet = true;
					tweet_locations = tweet_locations.concat(" [" + object.getString("latitude") + ", " + object.getString("longitude") + " ]");
				}
				JSONObject feature = new JSONObject("{\"type\":\"Feature\",\"id\":\""+Integer.toString(i)+"\""+
						",\"properties\":{\"name\":\"Air Quality Index\""+
						",\"density\":"+value+
						"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[["+
						(Double.parseDouble(object.getString("longitude"))+lng)+","+
						(Double.parseDouble(object.getString("latitude"))-lat)+"],["+
						(Double.parseDouble(object.getString("longitude"))+lng)+","+
						(Double.parseDouble(object.getString("latitude"))+lat)+"],["+
						(Double.parseDouble(object.getString("longitude"))-lng)+","+
						(Double.parseDouble(object.getString("latitude"))+lat)+"],["+
						(Double.parseDouble(object.getString("longitude"))-lng)+","+
						(Double.parseDouble(object.getString("latitude"))-lat)+"]]]}}");
				observationsData.getJSONObject("geojson").getJSONArray("features").put(feature);
			}
		}
		
		if (tweet) {
			try {
				updateTweet(getTwitterInstance(), "Pollution Alert in SymbCity! This alert comes from the following locations: " + tweet_locations);
			} catch (TwitterException e) {
				System.out.println(e.getMessage());
			}
		}

		if (!observationsData.getJSONObject("geojson").getJSONArray("features").isEmpty()) {
			MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
			MongoDatabase database = mongoClient.getDatabase("map");
			MongoCollection<Document> collection = database.getCollection("geojson");
			Document document = Document.parse(observationsData.toString());
			/*if (collection.find(eq("location", "Zagreb")).iterator().hasNext())
				collection.replaceOne(eq("location", location), document);
			else*/
				collection.insertOne(document);
			mongoClient.close();
		} else {
			System.out.println("504 error from Service");
		}
	
	}

}
