package gov.fda.ocrpoc;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.chemistry.opencmis.client.SessionParameterMap;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import gov.fda.ocrpoc.retry.FetchDocumentumPDFForOCRException;
import gov.fda.ocrpoc.retry.SolrProxyServiceNotAvailableException;

@SpringBootApplication
public class OcrProcessorApplication implements CommandLineRunner{
	
	static {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        System.setProperty("currenttime", dateFormat.format(new Date()));
    }
	
	private static final Logger LOGGER=LoggerFactory.getLogger(OcrProcessorApplication.class);
	
	@Autowired
	private OCR ocr;
	
	private int cmisCounter = 1;
	
	public static void main(String[] args) {
		new SpringApplicationBuilder(OcrProcessorApplication.class).run(args);
	}
	
	@Override
	public void run(String... args){
		int solrProxyCounter = 0;
		String configurationFile = args[0];
				
		try (InputStream input = new FileInputStream(configurationFile)) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            Cli cli = new Cli();
            CliConfig cliConfig = cli.parsepropertyFile(prop);
            ocr.OCRConfig(cliConfig);
            ocr.dateQuery(1);
            
        } catch (IOException ex) {
        	LOGGER.error("OcrPocApplication - IO Exception {1}", ex);
        } catch (Exception e) {
        	
        	if (e instanceof SolrProxyServiceNotAvailableException) {
				solrProxyCounter++;
				if (solrProxyCounter < 3 ) {
					ocr.dateQuery(1);
				} else {
					LOGGER.error("SolrProxyServiceNotAvailableException - Exception: {}", e.toString());
					ocr.initiateAppShutdown();
				}
			LOGGER.error("OcrPocApplication - Exception {1}", e);
        	} else if (e instanceof FetchDocumentumPDFForOCRException) {
        		LOGGER.error("FetchDocumentumPDFForOCRException - Exception: {}", e.toString());

				ocr.initiateAppShutdown();
        	}
        }
		
		LOGGER.info("Property configured");
        
	}
	
	
	
	@Bean
	public Session getRepoSession(@Autowired ConfigProps config) {
		if (cmisCounter > 3) {
			LOGGER.error("Network Connection Exception - Alfresco CMIS: {}", "3 attempts");
			System.exit(0);			
		}
		try {
			SessionFactory factory = SessionFactoryImpl.newInstance();
			SessionParameterMap sessionParam = new SessionParameterMap();
			sessionParam.setBasicAuthentication(config.getCmisUserName(), config.getCmisPassword());
			sessionParam.setBrowserBindingUrl(config.getCmisUrl());
			String repositoryID = factory.getRepositories(sessionParam).get(0).getId();
			sessionParam.setRepositoryId(repositoryID);
			Session repoSession = factory.createSession(sessionParam);
			return repoSession;
		} catch (Exception e) {
			LOGGER.error("AlfrescoCMISServiceNotAvailableException: {}", e.toString());
			cmisCounter++;
    		getRepoSession(config);

		}
		return null;
	}

}
