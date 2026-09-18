package pl.beone.archivelink.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.servlet.FormData;

@Slf4j
public class MultiPartFormUtil {

    /**
     * Extracts form fields from a multipart/formdata request
     *
     * @param request WebScript request
     * @return Array of FormFields
     * @throws WebScriptException When the request body is null, cannot be parsed, or no form fields could be extracted
     */
    public static FormData.FormField[] extractFormFields(WebScriptRequest request) {
        var formData = (FormData) request.parseContent();
        if (formData == null) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No content or cannot be parsed");
        }

        var formDataFields = formData.getFields();
        if (formDataFields.length == 0) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No form fields detected");
        }

        return formDataFields;
    }

}
