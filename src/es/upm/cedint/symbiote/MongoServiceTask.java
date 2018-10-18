package es.upm.cedint.symbiote;

import java.util.TimerTask;

public class MongoServiceTask extends TimerTask{

	private MongoService mongoService;
	
	@Override
	public void run() {
		if(mongoService == null) {
			mongoService = new MongoService();
		}
		mongoService.insertFeatureCollection(mongoService.createLocations(45.746620178222656, 15.836546897888184, 45.84677505493164, 16.080224990844727));
	}

}
