package com.hortonworks.iotas.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hortonworks.iotas.storage.Storable;

import java.util.Collection;


/**
 * <p>
 * A wrapper entity for passing entities and status back to the client.
 * </p>
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogResponse {

    /**
     * ResponseMessage args if any should always be string to keep it simple.
     */
    public enum ResponseMessage {
        SUCCESS(1000, "Success", 0),
        ENTITY_NOT_FOUND(1101, "Entity with id [%s] not found.", 1),
        EXCEPTION(1102, "An exception with message [%s] was thrown while processing request.", 1),
        BAD_REQUEST_PARAM_MISSING(1103, "Bad request. Param [%s] is missing or empty.", 1),
        DATASOURCE_TYPE_FILTER_NOT_FOUND(1104, "Datasource not found for type [%s], query params [%s].", 2),
        DATAFEED_FILTER_NOT_FOUND(1104, "Datafeed not found for query params [%s].", 1);

        private int code;
        private String msg;
        private int nargs;

        ResponseMessage(int code, String msg, int nargs) {
            this.code = code;
            this.msg = msg;
            this.nargs = nargs;
        }

        public static String format(ResponseMessage responseMessage, String... args) {
            //TODO: validate number of args
            return String.format(responseMessage.msg, args);
        }
    }

    /**
     * Response code.
     */
    private int responseCode;
    /**
     * Response message.
     */
    private String responseMessage;
    /**
     * For response that returns a single entity.
     */
    private Storable entity;
    /**
     * For response that returns a collection of entities.
     */
    private Collection<? extends Storable> entities;

    private CatalogResponse() {}

    public static class Builder {
        private ResponseMessage responseMessage;
        private Storable entity;
        private Collection<? extends Storable> entities;

        public Builder(ResponseMessage responseMessage) {
            this.responseMessage = responseMessage;
        }

        public Builder entity(Storable entity) {
            this.entity = entity;
            return this;
        }

        public Builder entities(Collection<? extends Storable> entities) {
            this.entities = entities;
            return this;
        }

        public CatalogResponse format(String... args) {
            CatalogResponse response = new CatalogResponse();
            response.responseCode = responseMessage.code;
            response.responseMessage = ResponseMessage.format(responseMessage, args);
            response.entity = entity;
            response.entities = entities;
            return response;
        }
    }

    public static Builder newResponse(ResponseMessage msg) {
        return new Builder(msg);
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public Storable getEntity() {
        return entity;
    }


    public Collection<? extends Storable> getEntities() {
        return entities;
    }


}