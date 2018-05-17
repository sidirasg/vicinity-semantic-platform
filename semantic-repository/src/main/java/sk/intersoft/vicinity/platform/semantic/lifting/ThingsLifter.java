package sk.intersoft.vicinity.platform.semantic.lifting;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.intersoft.vicinity.platform.semantic.lifting.model.ThingJSON;
import sk.intersoft.vicinity.platform.semantic.lifting.model.ThingsLifterResult;
import sk.intersoft.vicinity.platform.semantic.lifting.model.thing.*;
import sk.intersoft.vicinity.platform.semantic.ontology.NamespacePrefix;
import sk.intersoft.vicinity.platform.semantic.ontology.Namespaces;
import sk.intersoft.vicinity.platform.semantic.util.UniqueID;
import sk.intersoft.vicinity.platform.semantic.utils.JSONUtil;

import java.util.*;

public class ThingsLifter {
    public static String JSONLD_SCHEMA_LOCATION = System.getProperty("jsonld.schema.location");

    final static Logger logger = LoggerFactory.getLogger(ThingsLifter.class.getName());
    private Set<String> deviceTypes;
    private Set<String> serviceTypes;
    private Set<String> properties;


    public ThingsLifter(Set<String> deviceTypes, Set<String> serviceTypes, Set<String> properties) {
        this.deviceTypes = deviceTypes;
        this.serviceTypes = serviceTypes;
        this.properties = properties;

        logger.info("THINGS-LIFTER INITIALIZED WITH: ");
        logger.info("device types: " + this.deviceTypes);
        logger.info("service types: " + this.serviceTypes);
        logger.info("properties: " + this.properties);
    }

    private void instantiateObjects(JSONObject object) {
        if (object.has(ThingJSON.idAnnotation)) {
        } else {
            String id = UniqueID.create();
            String instance = Namespaces.prefixed(NamespacePrefix.thing, id);
            object.put(ThingJSON.idAnnotation, instance);
        }
        for (String key : object.keySet()) {
            Object value = object.get(key);
            if (value instanceof JSONObject) {
                instantiateObjects((JSONObject) value);
            } else if (value instanceof JSONArray) {
                JSONArray array = object.getJSONArray(key);
                Iterator i = array.iterator();
                while (i.hasNext()) {
                    Object item = i.next();
                    if (item instanceof JSONObject) {
                        instantiateObjects((JSONObject) item);
                    }
                }
            }

        }
    }



    private void liftLinks(InteractionPattern pattern) {

        if(pattern.readEndpoint != null) {
            InteractionPatternEndpoint endpoint = pattern.readEndpoint;
            endpoint.jsonExtension.put(ThingJSON.OUTPUT_RAW_JSON_STRING, DataSchema.toJSON(endpoint.output).toString());
        }
        if(pattern.writeEndpoint != null) {
            InteractionPatternEndpoint endpoint = pattern.writeEndpoint;
            endpoint.jsonExtension.put(ThingJSON.OUTPUT_RAW_JSON_STRING, DataSchema.toJSON(endpoint.output).toString());
            endpoint.jsonExtension.put(ThingJSON.INPUT_RAW_JSON_STRING, DataSchema.toJSON(endpoint.input).toString());
        }
    }

    private void liftProperties(ThingDescription thing, ThingValidator validator) {
        for (Map.Entry<String, InteractionPattern> entry : thing.properties.entrySet()) {
            InteractionPattern property = entry.getValue();
            property.jsonExtension.put(ThingJSON.typeAnnotation, Namespaces.prefixed(NamespacePrefix.wot, "Property"));

            if (!properties.contains(property.refersTo)) {
                validator.errors.add("unknown ontology property individual for [monitors]: [" + property.refersTo + "], thing ["+thing.oid+"] property ["+property.id+"]");
            }

            liftLinks(property);
        }
    }

    private void liftActions(ThingDescription thing, ThingValidator validator) {
        for (Map.Entry<String, InteractionPattern> entry : thing.actions.entrySet()) {
            InteractionPattern action = entry.getValue();
            action.jsonExtension.put(ThingJSON.typeAnnotation, Namespaces.prefixed(NamespacePrefix.wot, "Action"));

            if (!properties.contains(action.refersTo)) {
                validator.errors.add("unknown ontology property individual for [affects]: [" + action.refersTo + "], thing ["+thing.oid+"] action ["+action.id+"]");
            }

            liftLinks(action);
        }
    }

    private void liftEvents(ThingDescription thing, ThingValidator validator) {
        for (Map.Entry<String, InteractionPattern> entry : thing.events.entrySet()) {
            InteractionPattern event = entry.getValue();
            event.jsonExtension.put(ThingJSON.typeAnnotation, Namespaces.prefixed(NamespacePrefix.wot, "Event"));

            if (!properties.contains(event.refersTo)) {
                validator.errors.add("unknown ontology property individual for [monitors]: [" + event.refersTo + "], thing ["+thing.oid+"] event ["+event.id+"]");
            }

            event.jsonExtension.put(ThingJSON.OUTPUT_RAW_JSON_STRING, DataSchema.toJSON(event.output).toString());
        }
    }

    private void lift(ThingDescription thing, ThingValidator validator) {
        if(thing != null){
            thing.jsonExtension.put("@context", JSONLD_SCHEMA_LOCATION);
            thing.jsonExtension.put(ThingJSON.idAnnotation, Namespaces.prefixed(NamespacePrefix.thing, thing.oid));

            if (deviceTypes.contains(thing.type)) {
                thing.jsonExtension.put(ThingJSON.typeAnnotation, thing.type);
            } else {
                validator.errors.add("unknown ontology class for thing [type]: [" + thing.type + "]");
            }

            liftProperties(thing, validator);
            liftActions(thing, validator);
            liftEvents(thing, validator);


        }
        else {
            validator.errors.add("thing was not parsed, unable to lift it");
        }




    }

    public ThingsLifterResult lift(String data) {
        ThingValidator validator = new ThingValidator(false);
        try {
            JSONObject object = new JSONObject(data);
            try{
                ThingDescription thing = ThingDescription.create(object, validator);

                lift(thing, validator);

                if(!validator.failed()){

                    JSONObject lifted = ThingDescription.toJSON(thing);

                    instantiateObjects(lifted);

                    return new ThingsLifterResult(lifted);
                }
            }
            catch(Exception e){
                logger.error("", e);
            }
        }
        catch(Exception e) {
            logger.error("", e);
        }

        return new ThingsLifterResult(null, validator.errors);

    }
}
