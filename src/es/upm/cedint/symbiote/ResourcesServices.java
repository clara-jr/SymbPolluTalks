package es.upm.cedint.symbiote;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import eu.h2020.symbiote.client.AbstractSymbIoTeClientFactory;
import eu.h2020.symbiote.client.interfaces.CRAMClient;
import eu.h2020.symbiote.client.interfaces.RAPClient;
import eu.h2020.symbiote.client.interfaces.SearchClient;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsResponse;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.security.commons.exceptions.custom.SecurityHandlerException;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.*;

import static eu.h2020.symbiote.client.AbstractSymbIoTeClientFactory.*;

@Path("/")
public class ResourcesServices {

	private TimerTask mongoTask;

	private static String coreAddress = "https://symbiote-open.man.poznan.pl";
	private static String keystorePath = "keystore.jks";
	private static String keystorePassword = "keystore";
	private static Type type = Type.FEIGN;

	@Context
	private HttpServletResponse servletResponse;

	private void allowCrossDomainAccess() {
		if (servletResponse != null){
			servletResponse.setHeader("Access-Control-Allow-Origin", "*");
		}
	}

	@Path("initializeCollectionTask/")
	@GET
	public void initializeCollectionTask() {
		if (mongoTask == null) {
			this.mongoTask = new MongoServiceTask();
			Timer timer = new Timer();
			timer.schedule(mongoTask, 0, 3600000);
			System.out.println("MongoServiceTask started.");
		}
	}

	@Path("featureCollection/{location}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
	public Response getFeatureCollectionFromDB(@PathParam("location") String location) {
		MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
		MongoDatabase database = mongoClient.getDatabase("map");
		MongoCollection<Document> collection = database.getCollection("geojson");
		FindIterable<Document> docs = collection.find(eq("location", location));
		JSONArray result = new JSONArray();
		Document document = docs.first();
		for (Document doc : docs) {
			if (doc.getLong("time") > document.getLong("time")) document = doc;
		}
		result.put(document);
		mongoClient.close();
		return Response.status(200).entity(result.getJSONObject(0).getJSONObject("geojson").toString()).build();
	}

	@Path("featureCollection/{location}/{platformId}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
	public Response getFeatureCollection(@PathParam("location") String location, @PathParam("platformId") String platformId) {

		allowCrossDomainAccess();
		List<String> sensorType = new ArrayList<String>();
		List<Float> locationLatitude = new ArrayList<Float>();
		List<Float> locationLongitude = new ArrayList<Float>();
		List<Float> sensorValue=new ArrayList<Float>();
		List<Float> sensorValueAQI_Act=new ArrayList<Float>();
		List<Float> locationLatitudeAct=new ArrayList<Float>();
		List<Float> locationLongitudeAct=new ArrayList<Float>();
		ObjectMapper om = new ObjectMapper();
		om.enable(SerializationFeature.INDENT_OUTPUT);

		Config config = new Config(coreAddress, keystorePath, keystorePassword, type);

		AbstractSymbIoTeClientFactory factory = null;
		try {
			factory = getFactory(config);
		} catch (SecurityHandlerException | NoSuchAlgorithmException e) {
			return Response.status(e.hashCode()).entity(e.getMessage()).build();
		}

		SearchClient searchClient = factory.getSearchClient();

		CoreQueryRequest coreQueryRequest = new CoreQueryRequest.Builder()
				.locationName("*"+location+"*")
				.platformName("*"+platformId+"*")
				.build();

		String result = "[]";
		try {
			QueryResponse queryResponse = searchClient.searchAsGuest(coreQueryRequest, true);
			result = om.writeValueAsString(queryResponse.getResources());
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println(result);
		}

		JSONArray observations = new JSONArray();
		JSONArray jsonArray = new JSONArray(result);
		int i=0;
		for (int h = 0; h < jsonArray.length(); h++) {

			JSONObject jsonObject = jsonArray.getJSONObject(h);
			String resourceId = jsonObject.getString("id");;

			CRAMClient cramClient = factory.getCramClient();
			RAPClient rapClient = factory.getRapClient();

			ResourceUrlsResponse resourceUrlsResponse = cramClient.getResourceUrlAsGuest(resourceId, true);
			String resourceUrl = resourceUrlsResponse.getBody().get(resourceId);

			String resource = "{}";
			try {
				Observation observation = rapClient.getLatestObservationAsGuest(resourceUrl, true);
				resource = om.writeValueAsString(observation);

				JSONObject object = new JSONObject(resource);

				if(object!=null) {
					observations.put(object);

					locationLatitude.add(object.getJSONObject("location").getFloat("latitude"));
					locationLongitude.add(object.getJSONObject("location").getFloat("longitude"));

					sensorType.add(object.getJSONArray("obsValues").getJSONObject(0).getJSONObject("obsProperty").getString("name"));

					if (sensorType.get(i).contains("articulateMatter_LessThan_10um"))
					{
						sensorValue.add(object.getJSONArray("obsValues").getJSONObject(0).getFloat("value")*100/90);
					}
					else if(sensorType.get(i).contains("itrogen"))
					{
						sensorValue.add(object.getJSONArray("obsValues").getJSONObject(0).getFloat("value")*100/200);
					}
					else if(sensorType.get(i).contains("articulateMatter_LessThan_2"))
					{
						sensorValue.add(object.getJSONArray("obsValues").getJSONObject(0).getFloat("value")*100/55);
					}
					else if(sensorType.get(i).contains("arbon"))
					{
						sensorValue.add(object.getJSONArray("obsValues").getJSONObject(0).getFloat("value")*100/10);
					}
					else if(sensorType.get(i).contains("ulphur"))
					{
						sensorValue.add(object.getJSONArray("obsValues").getJSONObject(0).getFloat("value")*100/350);
					}
					else if(sensorType.get(i).contains("zone"))
					{
						sensorValue.add(object.getJSONArray("obsValues").getJSONObject(0).getFloat("value")*100/180);	
					}
					else {
						System.out.println("No type");
					}
					i++;
				}

			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.out.println(resource);
			}

		}

		locationLatitudeAct.add(locationLatitude.get(0));
		locationLongitudeAct.add(locationLongitude.get(0));
		sensorValueAQI_Act.add(sensorValue.get(0));

		for (i=1;i<locationLatitude.size();i++)
		{
			if(locationLatitudeAct.contains(locationLatitude.get(i))&& locationLongitudeAct.contains(locationLongitude.get(i)))
			{
				if(locationLatitudeAct.indexOf(locationLatitude.get(i))==locationLongitudeAct.indexOf(locationLongitude.get(i)))
				{
					if(sensorValueAQI_Act.get(locationLatitudeAct.indexOf(locationLatitude.get(i)))<sensorValue.get(i))
					{
						sensorValueAQI_Act.set(locationLatitudeAct.indexOf(locationLatitude.get(i)), sensorValue.get(i));
					}
				}
			}
			else {
				locationLatitudeAct.add(locationLatitude.get(i));
				locationLongitudeAct.add(locationLongitude.get(i));
				sensorValueAQI_Act.add(sensorValue.get(i));
			}
		}
		for (i=0;i<sensorValueAQI_Act.size();i++)
		{
			System.out.println(sensorValueAQI_Act.get(i));
			System.out.println(locationLatitudeAct.get(i));
			System.out.println(locationLongitudeAct.get(i));
		}
		JSONObject observationsData = new JSONObject("{\"location\":"+location+",\"geojson\": {\"type\":\"FeatureCollection\",\"features\":[]}}");
		for (i=0;i<sensorValueAQI_Act.size();i++)
		{
			double mult = 1.5;
			double lat = 0.003;
			double lng = lat*mult;
			JSONObject feature = new JSONObject("{\"type\":\"Feature\",\"id\":"+i+
					",\"properties\":{\"name\":\"Air Quality Index\""+
					",\"density\":"+sensorValueAQI_Act.get(i)+
					"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[["+
					(locationLongitudeAct.get(i)+lng)+","+
					(locationLatitudeAct.get(i)-lat)+"],["+
					(locationLongitudeAct.get(i)+lng)+","+
					(locationLatitudeAct.get(i)+lat)+"],["+
					(locationLongitudeAct.get(i)-lng)+","+
					(locationLatitudeAct.get(i)+lat)+"],["+
					(locationLongitudeAct.get(i)-lng)+","+
					(locationLatitudeAct.get(i)-lat)+"]]]}}");
			observationsData.getJSONObject("geojson").getJSONArray("features").put(feature);
		}

		return Response.status(200).entity(observationsData.getJSONObject("geojson").toString()).build();

	}

}
