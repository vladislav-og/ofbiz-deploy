package ee.taltech.accounting.connector.camel.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.util.Converters;
import org.apache.ofbiz.entity.util.EntityQuery;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class GenericService {

    Delegator delegator;
    public static final Converters.JSONToGenericValue jsonToGenericConverter = new Converters.JSONToGenericValue();

    public GenericService(Delegator delegator) {
        this.delegator = delegator;
    }

    public static final String module = GenericService.class.getName();

    public String getAll(String table) {
        List<GenericValue> items = new ArrayList<>();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            items = EntityQuery.use(delegator)
                    .from(table)
                    .queryList();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            GenericValue error = new GenericValue();
            error.put("Error", e);
            items.add(error);
        }

        items.forEach(i -> System.out.println(fetchWithChildren(i)));

        return gson.toJson(items);
    }

    private GenericValue fetchWithChildren(GenericValue item) {
        Iterator<ModelRelation> iterator = item.getModelEntity().getRelationsIterator();
        while (iterator.hasNext()) {
            ModelRelation relation = iterator.next();
            try {
                System.out.println(item.getRelated(relation.getCombinedName(), null, null, false));
                item.set(Character.toLowerCase(relation.getCombinedName().charAt(0)) + relation.getCombinedName().substring(1) + "Id", item.getRelated(relation.getCombinedName(), null, null, false));
            } catch (GenericEntityException e) {
                e.printStackTrace();
            }
        }
        return item;
    }

    public String getSingle(String table, String id, String idColumn) {
        if (StringUtils.isEmpty(idColumn))
            idColumn = table + "Id";

        List<GenericValue> items = new ArrayList<>();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            items = EntityQuery.use(delegator)
                    .from(table)
                    .where(EntityCondition.makeCondition(idColumn, id))
                    .queryList();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            GenericValue error = new GenericValue();
            error.put("Error", e);
            items.add(error);
        }
        return gson.toJson(items);
    }

    /**
     * @param table Table name as string
     * @param json  String form of an entity
     * @return response to say if success or not
     * @author big_data / REST api team
     */
    public Response create(String table, String json) {
        try {
            // uses custom method in the converter class that takes in delegator name, entity name and json
            // and spits out a GenericValue.
            // The converter "default" method with just GenericValue input wants the object to contain
            // _ENTITY_NAME_ and _DELEGATOR_NAME_ fields to be able to do the conversion.
            GenericValue object = jsonToGenericConverter.convert(delegator.getDelegatorName(), table, JSON.from(json));
            // incrementing the primary key ID, ofbiz takes care of it if PK is just one field
            object.setNextSeqId();
            // uses delegator's create() method that takes in a GenericValue and saves it into DB
            // it knows where to save it because genericvalue object knows what entity it is and what delegator it must use
            delegator.create(object);
            return Response.ok().type("application/json").build();
        } catch (GenericEntityException | ConversionException e) {
            e.printStackTrace();
            return Response.serverError().entity("Error of some sort").build();
        }
    }
}
