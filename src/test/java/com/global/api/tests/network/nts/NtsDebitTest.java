package com.global.api.tests.network.nts;

import com.global.api.ServicesContainer;
import com.global.api.entities.*;
import com.global.api.entities.enums.*;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.network.entities.NtsProductData;
import com.global.api.network.entities.NtsTag16;
import com.global.api.network.entities.nts.NtsDataCollectRequest;
import com.global.api.network.entities.nts.NtsRequestMessageHeader;
import com.global.api.network.enums.*;
import com.global.api.paymentMethods.*;
import com.global.api.serviceConfigs.AcceptorConfig;
import com.global.api.serviceConfigs.NetworkGatewayConfig;
import com.global.api.services.BatchService;
import com.global.api.tests.BatchProvider;
import com.global.api.tests.StanGenerator;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class NtsDebitTest {
    private DebitTrackData track;
    private NtsRequestMessageHeader ntsRequestMessageHeader;
    // gateway config
    NetworkGatewayConfig config;
    String emvTagData = "4F07A0000007681010820239008407A00000076810108A025A33950500800080009A032021039B02E8009C01005F280208405F2A0208405F3401019F1A0208409F0E0500400000009F0F05BCB08098009F10200FA502A830B9000000000000000000000F0102000000000000000000000000009F2103E800259F2608DD53340458AD69B59F2701809F34031E03009F3501169F3303E0F8C89F360200019F37045876B0989F3901009F4005F000F0A0019F410400000000";

    public NtsDebitTest() throws ApiException {
        Address address = new Address();
        address.setName("My STORE            ");
        address.setStreetAddress1("1 MY STREET       ");
        address.setCity("JEFFERSONVILLE  ");
        address.setPostalCode("90210");
        address.setState("KY");
        address.setCountry("USA");

        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.setAddress(address);
        ntsRequestMessageHeader = new NtsRequestMessageHeader();

        ntsRequestMessageHeader.setTerminalDestinationTag("478");
        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.PinDebit);
        
        ntsRequestMessageHeader.setPriorMessageResponseTime(999);
        ntsRequestMessageHeader.setPriorMessageConnectTime(999);
        ntsRequestMessageHeader.setPriorMessageCode("08");
        
        // data code values
        // acceptorConfig.setCardDataInputCapability(CardDataInputCapability.ContactlessEmv_ContactlessMsd_KeyEntry);
        acceptorConfig.setTerminalOutputCapability(TerminalOutputCapability.None);
        acceptorConfig.setCardDataInputCapability(CardDataInputCapability.ContactlessEmv_ContactlessMsd_KeyEntry);
        acceptorConfig.setCardHolderAuthenticationCapability(CardHolderAuthenticationCapability.PIN);
        acceptorConfig.setOperatingEnvironment(OperatingEnvironment.Attended);

        // hardware software config values
        acceptorConfig.setHardwareLevel("34");
        acceptorConfig.setSoftwareLevel("21205710");

        // pos configuration values
        acceptorConfig.setSupportsPartialApproval(true);
        acceptorConfig.setSupportsShutOffAmount(true);
        acceptorConfig.setSupportsReturnBalance(true);
        acceptorConfig.setSupportsDiscoverNetworkReferenceId(true);
        acceptorConfig.setSupportsAvsCnvVoidReferrals(true);

        // gateway config
        config = new NetworkGatewayConfig(Target.NTS);
        config.setPrimaryEndpoint("test.txns-c.secureexchange.net");
        config.setPrimaryPort(15031);
        config.setSecondaryEndpoint("test.txns.secureexchange.net");
        config.setSecondaryPort(15031);
        config.setEnableLogging(true);
        config.setStanProvider(StanGenerator.getInstance());
        config.setBatchProvider(BatchProvider.getInstance());
        config.setAcceptorConfig(acceptorConfig);

        // NTS Related configurations
        config.setBinTerminalId(" ");
        config.setBinTerminalType(" ");
        config.setInputCapabilityCode(CardDataInputCapability.ContactEmv_MagStripe);
        config.setTerminalId("21");
        config.setUnitNumber("00066654534");
        config.setSoftwareVersion("21");
        config.setLogicProcessFlag(LogicProcessFlag.Capable);
        config.setTerminalType(TerminalType.VerifoneRuby2Ci);

        ServicesContainer.configureService(config);

//        config.setMerchantType("5541");
        ServicesContainer.configureService(config, "ICR");


        ServicesContainer.configureService(config, "timeout");
        EncryptionData encryptionData = new EncryptionData();
        encryptionData.setKsn("A504010005E0003C    ");

        // debit card
        track = new DebitTrackData();
        track.setValue(";720002123456789=2512120000000000001?9  ");
         track.setEntryMethod(EntryMethod.Swipe); //For EMV test cases
//        track.setEntryMethod(EntryMethod.Magnetic_stripe_track2_data_unattended_AFD);
        track.setPinBlock("78FBB9DAEEB14E5A");
        track.setEncryptionData(encryptionData);
        track.setCardType("PinDebit");
    }

    private NtsTag16 getTag16() {
        NtsTag16 tag = new NtsTag16();
        tag.setPumpNumber(1);
        tag.setWorkstationId(1);
        tag.setServiceCode(ServiceCode.Full);
        tag.setSecurityData(SecurityData.CVN);
        return tag;
    }

    private NtsProductData getProductDataForNonFleetBankCards(IPaymentMethod method) throws ApiException {
        NtsProductData productData = new NtsProductData(ServiceLevel.FullServe, method);
        productData.addFuel(NtsProductCode.Diesel1, UnitOfMeasure.Gallons,10.24, 1.259);
        productData.addFuel(NtsProductCode.Diesel2, UnitOfMeasure.Gallons,20.24, 1.259);
        productData.addNonFuel(NtsProductCode.Batteries,UnitOfMeasure.NoFuelPurchased,1,10.74);
        productData.addNonFuel(NtsProductCode.CarWash,UnitOfMeasure.NoFuelPurchased,1,10.74);
        productData.addNonFuel(NtsProductCode.Dairy,UnitOfMeasure.NoFuelPurchased,1,10.74);
        productData.addNonFuel(NtsProductCode.Candy,UnitOfMeasure.NoFuelPurchased,1,10.74);
        productData.addNonFuel(NtsProductCode.Milk,UnitOfMeasure.NoFuelPurchased,1,10.74);
        productData.addNonFuel(NtsProductCode.OilChange,UnitOfMeasure.NoFuelPurchased,1,10.74);
        productData.setPurchaseType(PurchaseType.FuelAndNonFuel);
        productData.add(new BigDecimal("32.33"),new BigDecimal(0));
        return productData;
    }

    @Test //working
    public void test_PinDebit_with_Purchase_03() throws ApiException {

        Transaction response = track.charge(new BigDecimal(142))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        // check response
        assertEquals("00", response.getResponseCode());
    }


    @Test //working
    public void test_PinDebit_with_Purchase_03_EMV() throws ApiException {
        
        Transaction response = track.charge(new BigDecimal(142))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withTagData(emvTagData)
                .execute();
        assertNotNull(response);

        // check response
        assertEquals("00", response.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_pre_authorization_06() throws ApiException {

        Transaction response = track.authorize(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());
    }

    @Test //Working
    public void test_PinDebit_EMV_With_Track2Format_PreAuthorization_06() throws ApiException {

        Transaction response = track.authorize(new BigDecimal(10))
                .withCurrency("USD")
                .withUniqueDeviceId("0001")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withTagData(emvTagData)
                .execute();
        assertNotNull(response);
        assertEquals(response.getResponseMessage(), "00", response.getResponseCode());

    }

    @Test // Working
    public void test_PinDebit_Offline_Verified_EMV_With_Track2Format_PreAuthorization_06() throws ApiException {

        Transaction response = track.authorize(new BigDecimal(10))
                .withCurrency("USD")
                .withUniqueDeviceId("0001")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withTagData(emvTagData)
                .execute();
        assertNotNull(response);
        assertEquals(response.getResponseMessage(), "00", response.getResponseCode());

    }

    @Test // working
    public void test_PinDebit_pre_authorization_cancellation_without_TrackData_08() throws ApiException {

        Transaction preAuthorizationFundsResponse = track.authorize(new BigDecimal(100))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(preAuthorizationFundsResponse);
        assertEquals("00", preAuthorizationFundsResponse.getResponseCode());


        Transaction voidResponse = preAuthorizationFundsResponse.voidTransaction(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();

        assertEquals("00", voidResponse.getResponseCode());
    }

    //    @Test //not working
//    public void test_PinDebit_with_Purchase_03_MC_PartialApproval() throws ApiException {
//        ntsRequestMessageHeader.setntsRequestMessageHeader(ntsRequestMessageHeader);
//
//        config.setMessageCode(NtsMessageCode.DataCollectOrSale);
//        track.setEntryMethod(EntryMethod.Manual_attended); //For Master card
//        track.setCardType("MC");
//
//        Transaction response = track.charge(new BigDecimal(33))
//                .withCurrency("USD")
//                .withTransactionCode(TransactionCode.Purchase)
//               // .withntsRequestMessageHeader(ntsRequestMessageHeader)
//                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
//                .withUniqueDeviceId("0001")
//                .execute();
//        assertNotNull(response);
//
//        // check response
//        assertEquals("00", response.getResponseCode());
//    }
    @Test // working
    public void test_PinDebit_Purchase_03_With_DataCollect_02() throws ApiException {
        Transaction chargeResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(chargeResponse);
        // check response
        assertEquals("00", chargeResponse.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.DataCollectOrSale, chargeResponse, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.DataCollectOrSale);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test //working
    public void test_PinDebit_Purchase_03_With_DataCollect_02_With_UserData() throws ApiException {
        Transaction chargeResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(chargeResponse);

        assertEquals("00", chargeResponse.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.DataCollectOrSale, chargeResponse, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.DataCollectOrSale);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test //working
    public void test_PinDebit_Purchase_03_With_DataCollect_12() throws ApiException {
        Transaction chargeResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(chargeResponse);

        // check response
        assertEquals("00", chargeResponse.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.RetransmitDataCollect, chargeResponse, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.RetransmitDataCollect);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test //working
    public void test_PinDebit_Purchase_03_With_DataCollect_C2() throws ApiException {
        Transaction chargeResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(chargeResponse);

        // check response
        assertEquals("00", chargeResponse.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.ForceCollectOrForceSale, chargeResponse, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.ForceCollectOrForceSale);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test //working
    public void test_PinDebit_Purchase_03_With_DataCollect_D2() throws ApiException {
        Transaction chargeResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(chargeResponse);

        // check response
        assertEquals("00", chargeResponse.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.RetransmitForceCollect, chargeResponse, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.RetransmitForceCollect);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_purchase_with_cashBack_04() throws ApiException {

        Transaction response = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withCashBack(new BigDecimal(3))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_purchase_with_cashBack_04_With_DataCollect_02() throws ApiException {
        Transaction response = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withCashBack(new BigDecimal(3))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        // check response
        assertEquals("00", response.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.DataCollectOrSale, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.DataCollectOrSale);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_purchase_with_cashBack_04_With_DataCollect_12() throws ApiException {
        Transaction response = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withCashBack(new BigDecimal(3))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        // check response
        assertEquals("00", response.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.RetransmitDataCollect, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.RetransmitDataCollect);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_purchase_with_cashBack_04_With_DataCollect_C2() throws ApiException {
        Transaction response = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withCashBack(new BigDecimal(3))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        // check response
        assertEquals("00", response.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.ForceCollectOrForceSale, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.ForceCollectOrForceSale);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_purchase_with_cashBack_04_With_DataCollect_D2() throws ApiException {
        Transaction response = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withCashBack(new BigDecimal(3))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        // check response
        assertEquals("00", response.getResponseCode());
        String transactionDate = response.getNtsResponse().getNtsResponseMessageHeader().getTransactionDate();
        String transactionTime = response.getNtsResponse().getNtsResponseMessageHeader().getTransactionTime();

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.RetransmitForceCollect, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.RetransmitForceCollect);

        Transaction dataCollectResponse = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withCurrency("USD")
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .execute();
        assertNotNull(dataCollectResponse);

        // check response
        assertEquals("00", dataCollectResponse.getResponseCode());
    }

    @Test//
    public void test_PinDebit_purchase_refund_05() throws ApiException {

        Transaction response = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);
        assertEquals("00", response.getResponseCode());


        Transaction refund = track.refund(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withCurrency("USD")
                .execute();
        assertNotNull(refund);


        assertEquals("00", refund.getResponseCode());

    }

    @Test// working
    public void test_PinDebit_purchase_refund_05_with_Credit_Adjustment_03() throws ApiException {

        Transaction response = track.refund(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.CreditAdjustment);

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.CreditAdjustment, response, new BigDecimal(10));

        Transaction refund = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .withCurrency("USD")
                .execute();

        assertNotNull(refund);


        assertEquals("00", refund.getResponseCode());
    }

    @Test// working
    public void test_PinDebit_purchase_refund_05_with_Credit_Adjustment_03_With_UserData() throws ApiException {

        Transaction response = track.refund(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.CreditAdjustment, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.CreditAdjustment);

        Transaction refund = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .withCurrency("USD")
                .execute();

        assertNotNull(refund);


        assertEquals("00", refund.getResponseCode());
    }

    @Test// working
    public void test_PinDebit_purchase_refund_05_with_Credit_Adjustment_13() throws ApiException {

        Transaction response = track.refund(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.CreditAdjustment, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.RetransmitCreditAdjustment);

        Transaction refund = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .withCurrency("USD")
                .execute();

        assertNotNull(refund);

        assertEquals("00", refund.getResponseCode());
    }

    @Test// working
    public void test_PinDebit_purchase_refund_05_with_Credit_Adjustment_C3() throws ApiException {
        Transaction response = track.refund(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());

        String transactionDate = response.getNtsResponse().getNtsResponseMessageHeader().getTransactionDate();
        String transactionTime = response.getNtsResponse().getNtsResponseMessageHeader().getTransactionTime();

        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.ForceCreditAdjustment, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.ForceCreditAdjustment);

        Transaction refund = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .withCurrency("USD")
                .execute();

        assertNotNull(refund);


        assertEquals("00", refund.getResponseCode());
    }

    @Test// working
    public void test_PinDebit_purchase_refund_05_with_Credit_Adjustment_D3() throws ApiException {

        Transaction response = track.refund(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());


        // Data-Collect request preparation.
        NtsDataCollectRequest ntsDataCollectRequest = new NtsDataCollectRequest(NtsMessageCode.RetransmitForceCreditAdjustment, response, new BigDecimal(10));

        ntsRequestMessageHeader.setPinIndicator(PinIndicator.WithoutPin);
        ntsRequestMessageHeader.setNtsMessageCode(NtsMessageCode.RetransmitForceCreditAdjustment);

        Transaction refund = track.charge(new BigDecimal(10))
                .withTransactiontype(TransactionType.DataCollect)
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withNtsProductData(getProductDataForNonFleetBankCards(track))
                .withNtsTag16(getTag16())
                .withNtsDataCollectRequest(ntsDataCollectRequest)
                .withCurrency("USD")
                .execute();

        assertNotNull(refund);


        assertEquals("00", refund.getResponseCode());
    }

    @Test//working
    public void test_PinDebit_pre_authorization_ICR_06() throws ApiException {
        Transaction response = track.authorize(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute("ICR");
        assertNotNull(response);

        assertEquals("00", response.getResponseCode());
    }

    @Test //working
    public void test_PinDebit_pre_authorization_completion_without_TrackData_07() throws ApiException {
        Transaction preAuthorizationResponse = track.authorize(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(preAuthorizationResponse);

        assertEquals("00", preAuthorizationResponse.getResponseCode());

        Transaction captureResponse = preAuthorizationResponse.preAuthCompletion(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withSettlementAmount(new BigDecimal(10))
                .execute();

        // check response
        assertEquals("00", captureResponse.getResponseCode());
    }

    @Test // working
    public void test_PinDebit_pre_authorization_completion_ICR_without_TrackData_07() throws ApiException {

        Transaction preAuthorizationResponse = track.authorize(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute("ICR");
        assertNotNull(preAuthorizationResponse);

        assertEquals("00", preAuthorizationResponse.getResponseCode());


        Transaction captureResponse = preAuthorizationResponse.preAuthCompletion(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withSettlementAmount(new BigDecimal(10))
                .execute();

        // check response
        assertEquals("00", captureResponse.getResponseCode());
    }

    @Test // working
    public void test_PinDebit_pre_authorization_Cancellation_ICR_without_TrackData_08() throws ApiException {
        Transaction preAuthorizationFundsResponse = track.authorize(new BigDecimal(100))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute("ICR");
        assertNotNull(preAuthorizationFundsResponse);
        assertEquals("00", preAuthorizationFundsResponse.getResponseCode());

        String transactionDate = preAuthorizationFundsResponse.getNtsResponse().getNtsResponseMessageHeader().getTransactionDate();
        String transactionTime = preAuthorizationFundsResponse.getNtsResponse().getNtsResponseMessageHeader().getTransactionTime();

        Transaction voidResponse = preAuthorizationFundsResponse.voidTransaction(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withTransactionDate(transactionDate)
                .withTransactionTime(transactionTime)
                .execute();

        // check response
        assertEquals("00", voidResponse.getResponseCode());
    }

    @Test //Need to fix this TC
    @Ignore
    public void test_000_batch_close() throws ApiException {
        BatchSummary summary = BatchService.closeBatch();
        assertNotNull(summary);
        assertTrue(summary.isBalanced());
    }

    @Test//working
    public void test_pinDebit_purchase_reversal_13() throws ApiException {
        Transaction reversalResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(reversalResponse);
        assertEquals("00", reversalResponse.getResponseCode());

        String transactionDate = reversalResponse.getNtsResponse().getNtsResponseMessageHeader().getTransactionDate();
        String transactionTime = reversalResponse.getNtsResponse().getNtsResponseMessageHeader().getTransactionTime();

        Transaction refund = reversalResponse.reverse(new BigDecimal(10))
                .withTransactionDate(transactionDate)
                .withTransactionTime(transactionTime)
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withCurrency("USD")
                .execute();
        assertNotNull(refund);

        assertEquals("00", refund.getResponseCode());

    }

    @Test// working
    public void test_purchase_with_cashBack_reversal_14() throws ApiException {
        Transaction reversalResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withCashBack(new BigDecimal(3))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .execute();
        assertNotNull(reversalResponse);

        assertEquals("00", reversalResponse.getResponseCode());

        String transactionDate = reversalResponse.getNtsResponse().getNtsResponseMessageHeader().getTransactionDate();
        String transactionTime = reversalResponse.getNtsResponse().getNtsResponseMessageHeader().getTransactionTime();

        Transaction reversal = reversalResponse.reverse(new BigDecimal(10))
                .withTransactionDate(transactionDate)
                .withTransactionTime(transactionTime)
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withSettlementAmount(new BigDecimal(3))
                .withCurrency("USD")
                .execute();

        assertEquals("00", reversal.getResponseCode());
    }

    @Test// working
    //A Purchase Return Reversal message is used when a time-out or a HOST RESPONSE CODE
    //80 is received on a Purchase Return (05).
    public void test_purchase_refund_reversal_15() throws ApiException {

        Transaction refundResponse = track.refund(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withCurrency("USD")
                .execute();
        assertNotNull(refundResponse);

        assertEquals("00", refundResponse.getResponseCode());
        Transaction reversalResponse = refundResponse.reverse(new BigDecimal(10))

                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withCurrency("USD")
                .execute();
        assertNotNull(reversalResponse);

        assertEquals("00", reversalResponse.getResponseCode());
    }


    @Test//working
    public void test_pinDebit_purchase_reversal_13_With_EMV() throws ApiException {
        Transaction reversalResponse = track.charge(new BigDecimal(10))
                .withCurrency("USD")
                .withUniqueDeviceId("0001")
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withTagData(emvTagData)
                .execute();
        assertNotNull(reversalResponse);
        assertEquals("00", reversalResponse.getResponseCode());

        Transaction refund = reversalResponse.reverse(new BigDecimal(10))
                .withNtsRequestMessageHeader(ntsRequestMessageHeader)
                .withCurrency("USD")
                .execute();
        assertNotNull(refund);

        assertEquals("00", refund.getResponseCode());

    }
}