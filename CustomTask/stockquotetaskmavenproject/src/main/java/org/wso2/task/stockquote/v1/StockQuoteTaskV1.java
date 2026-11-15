package org.wso2.task.stockquote.v1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.startup.Task;
import org.apache.synapse.util.PayloadHelper;

public class StockQuoteTaskV1 implements Task, ManagedLifecycle {
private final Log log = LogFactory.getLog(StockQuoteTaskV1.class);
private String sequenceName;

    private String stockFile;

    private String injectTo;
    private SynapseEnvironment synapseEnvironment;

    public void execute() {
        log.debug("PlaceStockOrderTask begin");

        if (synapseEnvironment == null) {
            log.error("Synapse Environment not set");
            return;
        }

        if (injectTo == null) {
            log.error("to not set");
            return;
        }

        if (sequenceName == null || sequenceName.isEmpty()) {
            log.error("Sequence name not specified");
            return;
        }

        File existFile = new File(stockFile);

        if (!existFile.exists()) {
            log.debug("waiting for stock file");
            return;
        }

        SequenceMediator seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration().
                getSequence(sequenceName);
        if (seq != null) {
            if (log.isDebugEnabled()) {
                log.debug("injecting message to sequence : " + sequenceName);
            }

            try {

                // file format IBM,100,120.50

                BufferedReader reader = new BufferedReader(new FileReader(stockFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    String[] split = line.split(",");
                    String symbol = split[0];
                    String quantity = split[1];
                    String price = split[2];
                    MessageContext mc = synapseEnvironment.createMessageContext();
                    mc.setTo(new EndpointReference("http://localhost:9000/soap/SimpleStockQuoteService"));
                    mc.setSoapAction("urn:placeOrder");
                    mc.setProperty("OUT_ONLY", "true");
                    OMElement placeOrderRequest = createPlaceOrderRequest(symbol, quantity, price);
                    PayloadHelper.setXMLPayload(mc, placeOrderRequest);
                    synapseEnvironment.injectAsync(mc, seq);
                    log.info("placed order symbol:" + symbol + " quantity:" + quantity + " price:" + price);
                }
                reader.close();
            } catch (IOException e) {
                throw new SynapseException("error reading file", e);
            }
        }
        // Copy the existing file to the same directory and rename it with timestamp appended to the file name
        File newFile = new File(stockFile.replace(".txt", "") + "." + System.currentTimeMillis() + ".txt");
        try {
            Files.copy(existFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Error while copying the file", e);
            throw new SynapseException("Error while copying the file", e);
        }
        log.debug("PlaceStockOrderTask end");
    }

    public static OMElement createPlaceOrderRequest(String symbol, String qty, String purchPrice) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMNamespace ns = factory.createOMNamespace("http://services.samples/xsd", "m0");
        OMElement placeOrder = factory.createOMElement("placeOrder", ns);
        OMElement order = factory.createOMElement("order", ns);
        OMElement price = factory.createOMElement("price", ns);
        OMElement quantity = factory.createOMElement("quantity", ns);
        OMElement symb = factory.createOMElement("symbol", ns);
        price.setText(purchPrice);
        quantity.setText(qty);
        symb.setText(symbol);
        order.addChild(price);
        order.addChild(quantity);
        order.addChild(symb);
        placeOrder.addChild(order);
        return placeOrder;
    }

    public void destroy() {}

    public void init(SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }

    public SynapseEnvironment getSynapseEnvironment() {
        return synapseEnvironment;
    }

    public void setSynapseEnvironment(SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }

    public void setSequenceName(String sequenceName) {
        this.sequenceName = sequenceName;
    }

    public void setInjectTo(String injectTo) {
        this.injectTo = injectTo;
    }

    public String getStockFile() {
        return stockFile;
    }

    public void setStockFile(String stockFile) {
        this.stockFile = stockFile;
    }
}
