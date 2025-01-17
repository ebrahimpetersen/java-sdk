package com.global.api.network.entities;

import com.global.api.builders.AuthorizationBuilder;
import com.global.api.builders.ManagementBuilder;
import com.global.api.builders.TransactionBuilder;
import com.global.api.entities.EncryptionData;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.*;
import com.global.api.network.entities.nts.NtsRequestToBalanceData;
import com.global.api.network.entities.nts.NtsDataCollectRequest;
import com.global.api.network.elements.DE63_ProductDataEntry;
import com.global.api.network.enums.*;
import com.global.api.paymentMethods.*;
import com.global.api.serviceConfigs.AcceptorConfig;
import com.global.api.utils.EmvData;
import com.global.api.utils.EmvUtils;
import com.global.api.utils.NtsUtils;
import com.global.api.utils.StringUtils;
import org.joda.time.DateTime;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

public class UserDataTag {

    private UserDataTag() {
        throw new IllegalStateException("UserDataTag.class");
    }

    public static String getBankCardUserData(TransactionBuilder<Transaction> builder, IPaymentMethod paymentMethod,
                                             NTSCardTypes cardType, NtsMessageCode messageCode, AcceptorConfig acceptorConfig) {

        TransactionModifier modifier = builder.getTransactionModifier();
        TransactionType transactionType = builder.getTransactionType();

        String cvn = null;
        if (paymentMethod instanceof ICardData) {
            cvn = ((ICardData) paymentMethod).getCvn();
            cvn = StringUtils.padRight(cvn, 4, ' ');
        }

        boolean isAVSUsed = StringUtils.isNullOrEmpty(cvn);

        String uniqueDeviceId = StringUtils.padRight(builder.getUniqueDeviceId(), 4, ' ');

        StringBuilder sb = new StringBuilder();

        String amount = StringUtils.toNumeric(builder.getAmount());

        int totalNoOfTags = 0; // Tag counter.

        // 01 Function Code
        if (!StringUtils.isNullOrEmpty(builder.getTagData()) || transactionType.equals(TransactionType.Void) || transactionType.equals(TransactionType.Balance)) {
            String functionCode = null;
            if ((messageCode == NtsMessageCode.DataCollectOrSale && builder.getTransactionModifier() == TransactionModifier.Offline) ||
                    messageCode == NtsMessageCode.ForceCollectOrForceSale)
                functionCode = FunctionCode.OfflineApprovedSaleAdvice.getValue();
            else if (messageCode == NtsMessageCode.AuthorizationOrBalanceInquiry && modifier != TransactionModifier.ChipDecline) {
                BigDecimal tranAmount = StringUtils.toFractionalAmount(amount);
                if (tranAmount.equals(new BigDecimal(0))) {
                    functionCode = FunctionCode.BalanceInquiry.getValue();
                }
            } else if (messageCode == NtsMessageCode.DataCollectOrSale
                    || (modifier == TransactionModifier.ChipDecline && messageCode == NtsMessageCode.AuthorizationOrBalanceInquiry)) {
                functionCode = FunctionCode.OfflineDeclineAdvice.getValue();
            } else if (messageCode == NtsMessageCode.ReversalOrVoid ||
                    messageCode == NtsMessageCode.ForceReversalOrForceVoid) {
                functionCode = FunctionCode.Void.getValue();
            }
            if (!StringUtils.isNullOrEmpty(functionCode)) {
                totalNoOfTags++; // Increment the counter if tag is used.
                sb.append(UserDataTagId.FunctionCode.getValue()).append("\\");
                sb.append(functionCode).append("\\");
            }
        }


        // 02 TerminalCapability
        if (acceptorConfig.hasPosConfiguration_BankcardData()) {
            sb.append(UserDataTagId.TerminalCapability.getValue()).append("\\");
            sb.append(acceptorConfig.getTerminalCapabilityForBankcard()).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }
        // 03 Stan
        if ((messageCode == NtsMessageCode.ReversalOrVoid || messageCode == NtsMessageCode.ForceReversalOrForceVoid)
                && paymentMethod instanceof TransactionReference) {
            TransactionReference reference = (TransactionReference) paymentMethod;
            sb.append(UserDataTagId.Stan.getValue()).append("\\");
            sb.append(reference.getUserDataTag().get("03")).append("\\"); // Get from host response area
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        // 04 and 05 is expected in host response are not in request.
        // 06

        // 07 ZipCode
        if (!(cardType.equals(NTSCardTypes.MastercardFleet) || cardType.equals(NTSCardTypes.VisaFleet))
                && isAVSUsed
                && ((transactionType.equals(TransactionType.Auth) || transactionType.equals(TransactionType.Sale))
                && acceptorConfig.getAddress() != null)) {
            sb.append(UserDataTagId.ZipCode.getValue()).append("\\");
            sb.append(StringUtils.padRight(acceptorConfig.getAddress().getPostalCode(), 9, ' ')).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }


        // 08 FleetAuthData
        if (cardType.equals(NTSCardTypes.MastercardFleet) || cardType.equals(NTSCardTypes.VisaFleet)) {
            FleetData fleetData = builder.getFleetData();
            if ((transactionType.equals(TransactionType.Auth) ||
                    transactionType.equals(TransactionType.Sale) || transactionType.equals(TransactionType.DataCollect)) && fleetData != null) {
                sb.append(UserDataTagId.FleetAuthData.getValue()).append("\\");
                sb.append(getFleetDataTag08(fleetData, cardType));
                sb.append("\\");
                totalNoOfTags++;
            }
        }

        //09
        if ((cardType.equals(NTSCardTypes.MastercardFleet) || cardType.equals(NTSCardTypes.VisaFleet))
                && ((transactionType.equals(TransactionType.Sale) || transactionType.equals(TransactionType.DataCollect))
                && builder.getNtsProductData() != null)) {
            sb.append(UserDataTagId.ProductDataTag.getValue()).append("\\");
            sb.append(getProductDataTag09(builder, cardType));
            sb.append("?");
            sb.append("\\"); // Added separator
            totalNoOfTags++;

        } else if ((cardType.equals(NTSCardTypes.Mastercard)
                || cardType.equals(NTSCardTypes.Visa)
                || cardType.equals(NTSCardTypes.AmericanExpress)
                || cardType.equals(NTSCardTypes.Discover))
                && (transactionType.equals(TransactionType.Sale)
                && builder.getNtsProductData() != null)) {
            sb.append(UserDataTagId.ProductDataTag.getValue()).append("\\");
            sb.append(getProductDataTag09(builder, cardType));
            sb.append("\\"); // Added separator
            totalNoOfTags++;

        }

        // 10 Reserved

        //11 BanknetRefId & 12 Settlement Date
        if ((cardType.equals(NTSCardTypes.Mastercard) || cardType.equals(NTSCardTypes.MastercardFleet))
                && (transactionType.equals(TransactionType.Void) ||
                transactionType.equals(TransactionType.Balance))
                && (paymentMethod instanceof TransactionReference)) {
            TransactionReference reference = (TransactionReference) paymentMethod;
            sb.append(UserDataTagId.BanknetRefId.getValue()).append("\\");
            sb.append(reference.getUserDataTag().get("11")).append("\\"); // Get from host response area
            totalNoOfTags++; // Increment the counter if tag is used.
            sb.append(UserDataTagId.SettlementDate.getValue()).append("\\"); // 12 Settlement Date
            sb.append(reference.getUserDataTag().get("12")).append("\\"); // Get from host response area
            totalNoOfTags++; // Increment the counter if tag is used.
        }


        // 13 Cvn
        if ((cardType.equals(NTSCardTypes.Mastercard)
                || cardType.equals(NTSCardTypes.Visa)
                || cardType.equals(NTSCardTypes.Discover)
                || cardType.equals(NTSCardTypes.AmericanExpress))
                && !isAVSUsed
                && (transactionType.equals(TransactionType.Auth)
                || transactionType.equals(TransactionType.Sale)
                || messageCode.equals(NtsMessageCode.AuthorizationOrBalanceInquiry))) {
            sb.append(UserDataTagId.Cvn.getValue()).append("\\");
            sb.append(cvn).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.

        }

        // 14 Discover Network Ref Id
        if ((cardType.equals(NTSCardTypes.Discover) && (messageCode == NtsMessageCode.ReversalOrVoid ||
                messageCode == NtsMessageCode.ForceReversalOrForceVoid)) && (paymentMethod instanceof TransactionReference)) {
            TransactionReference reference = (TransactionReference) paymentMethod;
            sb.append(UserDataTagId.DiscoverNetworkRefId.getValue()).append("\\");
            sb.append(reference.getUserDataTag().get("14") + "\\"); // Get from host response area
            totalNoOfTags++; // Increment the counter if tag is used.

        }

        // 15 Reserved

        // 16
        if (builder.getNtsTag16() != null) {
            sb.append(UserDataTagId.Tag16.getValue()).append("\\");
            sb.append(getTagData16(builder.getNtsTag16())).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        //17 Card Sequence No // Only for EMV
        if (!StringUtils.isNullOrEmpty(builder.getTagData()) && builder.getCardSequenceNumber() != null) {
            sb.append(UserDataTagId.CardSequenceNumber.getValue()).append("\\");
            sb.append(builder.getCardSequenceNumber()).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        // 18 Visa Transaction Id
        if ((cardType.equals(NTSCardTypes.Visa) || cardType.equals(NTSCardTypes.VisaFleet)) && transactionType.equals(TransactionType.Void)
                && (paymentMethod instanceof TransactionReference)) {
            TransactionReference reference = (TransactionReference) paymentMethod;
            sb.append(UserDataTagId.VisaTransactionId.getValue()).append("\\");
            sb.append(reference.getUserDataTag().get("18")).append("\\"); // Get from host response area (left justify)
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        // 19

        // 20 Cash Over Amount
        if ((cardType.equals(NTSCardTypes.Discover))
                && (transactionType.equals(TransactionType.Sale)
                && ((AuthorizationBuilder) builder).getCashBackAmount() != null)) {
            sb.append(UserDataTagId.CashOverAmount.getValue()).append("\\");
            sb.append(StringUtils.toNumeric(((AuthorizationBuilder) builder).getCashBackAmount(), 6)).append("\\"); // Check desc
            totalNoOfTags++; // Increment the counter if tag is used.

        }

        // 21 Unique Device Id // Only for EMV
        if (!transactionType.equals(TransactionType.Void) && !StringUtils.isNullOrEmpty(uniqueDeviceId)) {
            sb.append(UserDataTagId.UniqueDeviceId.getValue()).append("\\");
            sb.append(uniqueDeviceId).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        // 22 Emv Pin Block // Only for EMV, 23 Emv Ksn // Only for EMV,  24 Emv Max Pin Entry // Only for EMV & 25 Emv Chip Auth Code
        if (!transactionType.equals(TransactionType.Void) && !StringUtils.isNullOrEmpty(builder.getTagData())) {
            if (messageCode.equals(NtsMessageCode.AuthorizationOrBalanceInquiry) || messageCode.equals(NtsMessageCode.DataCollectOrSale)) {
                if (paymentMethod instanceof IPinProtected) {
                    String pinBlock = ((IPinProtected) paymentMethod).getPinBlock();
                    if (!StringUtils.isNullOrEmpty(pinBlock)) {
                        sb.append(UserDataTagId.EmvPinBlock.getValue()).append("\\"); // 22 Emv Pin Block
                        sb.append(pinBlock).append("\\");
                        totalNoOfTags++; // Increment the counter if tag is used.
                    }
                }
                if (paymentMethod instanceof IEncryptable) {
                    EncryptionData encryptionData = ((IEncryptable) paymentMethod).getEncryptionData();
                    if (encryptionData != null) {
                        sb.append(UserDataTagId.EmvKsn.getValue()).append("\\"); // 23 Emv Ksn // Only for EMV
                        sb.append(StringUtils.padLeft(encryptionData.getKsn(), 20, ' ')).append("\\");
                        totalNoOfTags++; // Increment the counter if tag is used.
                    }
                }
                if (builder.getEmvMaxPinEntry() != null) {
                    sb.append(UserDataTagId.EmvMaxPinEntry.getValue()).append("\\"); // 24 Emv Max Pin Entry
                    sb.append(builder.getEmvMaxPinEntry()).append("\\");
                    totalNoOfTags++; // Increment the counter if tag is used.
                }
            }

            if (modifier == TransactionModifier.Offline
                    || modifier == TransactionModifier.ChipDecline) {
                sb.append(UserDataTagId.EmvChipAuthCode.getValue()).append("\\"); // 25 Emv Chip Auth Code
                if (messageCode == NtsMessageCode.DataCollectOrSale ||
                        messageCode == NtsMessageCode.ForceCollectOrForceSale)
                    sb.append(EmvAuthCode.OfflineApproved.getValue()).append("\\");
                else if (messageCode == NtsMessageCode.AuthorizationOrBalanceInquiry)
                    sb.append(EmvAuthCode.OfflineDeclined.getValue()).append("\\");
                else if (messageCode == NtsMessageCode.ReversalOrVoid ||
                        messageCode == NtsMessageCode.ForceReversalOrForceVoid)
                    sb.append(EmvAuthCode.UnableToGoOnlineOfflineApproved.getValue()).append("\\");
                else
                    sb.append(EmvAuthCode.UnableToGoOnlineOfflineDeclined.getValue()).append("\\");
                totalNoOfTags++; // Increment the counter if tag is used.
            }
        }

        // 26 Goods Sold
        if (cardType.equals(NTSCardTypes.AmericanExpress) && (transactionType.equals(TransactionType.Auth) || transactionType.equals(TransactionType.Sale))) {
            sb.append(UserDataTagId.GoodsSold.getValue()).append("\\");
            sb.append(((AuthorizationBuilder) builder).getGoodsSold()).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }


        // 27 Reserved

        // 28 Ecommerce Data1
        if ((cardType.equals(NTSCardTypes.Visa) || cardType.equals(NTSCardTypes.VisaFleet) ||
                cardType.equals(NTSCardTypes.AmericanExpress) || cardType.equals(NTSCardTypes.Discover) || cardType.equals(NTSCardTypes.PayPal)) &&
                ((transactionType.equals(TransactionType.Auth) || transactionType.equals(TransactionType.Sale))
                        && builder.getEcommerceData1() != null)) {
            sb.append(UserDataTagId.EcommerceData1.getValue()).append("\\");
            sb.append(builder.getEcommerceData1()).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.

        }

        // 29 Ecommerce Data2
        if ((cardType.equals(NTSCardTypes.Visa) || cardType.equals(NTSCardTypes.VisaFleet) ||
                cardType.equals(NTSCardTypes.AmericanExpress) || cardType.equals(NTSCardTypes.Discover) || cardType.equals(NTSCardTypes.PayPal))
                && ((transactionType.equals(TransactionType.Auth) || transactionType.equals(TransactionType.Sale)) && builder.getEcommerceData2() != null)) {
            sb.append(UserDataTagId.EcommerceData2.getValue()).append("\\");
            sb.append(builder.getEcommerceData2()).append("\\");
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        // 30 MCUCAF // For E-com entry methods, 31 MCWalletId // For all E-com entry methods & 32 MCSLI // For all E-com entry methods
        if (((cardType.equals(NTSCardTypes.Mastercard) || cardType.equals(NTSCardTypes.MastercardFleet) ||
                cardType.equals(NTSCardTypes.MastercardPurchasing)) && (transactionType.equals(TransactionType.Auth) || transactionType.equals(TransactionType.Sale)))
                && (paymentMethod instanceof ITrackData)) {
            ITrackData trackData = (ITrackData) builder.getPaymentMethod();
            NTSEntryMethod entryMethod=NtsUtils.isAttendedOrUnattendedEntryMethod(trackData.getEntryMethod(),trackData.getTrackNumber(),acceptorConfig.getOperatingEnvironment());
            if (entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataAttended ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataUnattendedAfd ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataUnattendedCat ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataUnattended) {
                sb.append(UserDataTagId.MCUCAF.getValue()).append("\\");
                sb.append("" + "\\");
                totalNoOfTags++; // Increment the counter if tag is used.
                sb.append(UserDataTagId.MCWalletId.getValue()).append("\\"); // 31 MCWalletId // For all E-com entry methods
                sb.append("" + "\\");
                totalNoOfTags++; // Increment the counter if tag is used.
                sb.append(UserDataTagId.MCSLI.getValue()).append("\\"); // 32 MCSLI // For all E-com entry methods
                sb.append("" + "\\");
                totalNoOfTags++; // Increment the counter if tag is used.
            }
        }


        // 33 Ecommerce Auth Indicator & 34 Ecommerce Merchant Order No
        if ((transactionType.equals(TransactionType.Auth) || transactionType.equals(TransactionType.Sale)) && ((cardType.equals(NTSCardTypes.VisaFleet)) || (cardType.equals(NTSCardTypes.MastercardFleet))) && (paymentMethod instanceof ITrackData)) {
            ITrackData trackData = (ITrackData) builder.getPaymentMethod();
            NTSEntryMethod entryMethod= NtsUtils.isAttendedOrUnattendedEntryMethod(trackData.getEntryMethod(),trackData.getTrackNumber(),acceptorConfig.getOperatingEnvironment());
            if (entryMethod == NTSEntryMethod.ECommerceNoTrackDataAttended ||
                    entryMethod == NTSEntryMethod.ECommerceNoTrackDataUnattendedAfd ||
                    entryMethod == NTSEntryMethod.ECommerceNoTrackDataUnattended ||
                    entryMethod == NTSEntryMethod.ECommerceNoTrackDataUnattendedCat ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataAttended ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataUnattendedAfd ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataUnattendedCat ||
                    entryMethod == NTSEntryMethod.SecureEcommerceNoTrackDataUnattended) {
                sb.append(UserDataTagId.EcommerceAuthIndicator.getValue() + "\\"); // 33 Ecommerce Auth Indicator // For all E-com entry methods
                sb.append(builder.getEcommerceAuthIndicator() + "\\");
                totalNoOfTags++; // Increment the counter if tag is used.

                if (builder.getInvoiceNumber() != null) {
                    sb.append(UserDataTagId.EcommerceMerchantOrderNumber.getValue() + "\\"); // 34 Ecommerce Merchant Order No
                    sb.append(builder.getInvoiceNumber() + "\\");
                    totalNoOfTags++; // Increment the counter if tag is used.
                }
            }
        }


        // 99 Integrated Circuit Card
        if (!StringUtils.isNullOrEmpty(builder.getTagData())) {
            sb.append(UserDataTagId.IntegratedCircuitCard.getValue()).append("\\");
            EmvData tagData = EmvUtils.parseTagData(builder.getTagData(), true);
            sb.append(tagData.getAcceptedTagData()); // Check EMV fallback
            totalNoOfTags++; // Increment the counter if tag is used.
        }

        // Removing the unwanted '\' char.
        if (sb.charAt(sb.length() - 1) == '\\') {
            sb.delete(sb.length() - 1, sb.length());
        }

        // Adding the number of tags.
        return StringUtils.padLeft(totalNoOfTags, 2, '0') + "\\" + sb.toString();
    }

    private static int mapServiceByCardType(ServiceLevel serviceLevel,NTSCardTypes ntsCardTypes) {
        switch (ntsCardTypes)
        {
            case VoyagerFleet:
                return mapServiceVoyager(serviceLevel);
            case WexFleet:
                return mapServiceWexFleet(serviceLevel);
            case FleetWide:
            case FuelmanFleet:
            case MastercardFleet:
                return mapService(serviceLevel);
            default:
                return 0;
        }
    }
    public static int mapService(ServiceLevel serviceLevel) {
        switch (serviceLevel) {
            case SelfServe:
                return 1;
            case FullServe:
                return 2;
            case Other_NonFuel:
                return 3;
            case NoFuelPurchased:
            default:
                return 0;
        }
    }
    public static int mapServiceWexFleet(ServiceLevel serviceLevel) {
        switch (serviceLevel) {
            case FullServe:
                return 01;
            case SelfServe:
                return 02;
            case NoFuelPurchased:
            default:
                return 0;

        }
    }


    public static int mapServiceVoyager(ServiceLevel serviceLevel) {
        switch (serviceLevel) {
            case FullServe:
                return 1;
            case Other:
                return 2;
            case SelfServe:
                return 0;
            case Unknown:
            default:
                return 9;
        }
    }

    public static int mapUnitMeasure(UnitOfMeasure unitOfMeasure) {
        switch (unitOfMeasure) {
            case CaseOrCarton:
                return 1;
            case Gallons:
                return 2;
            case Kilograms:
                return 3;
            case Liters:
                return 4;
            case Pounds:
                return 5;
            case Quarts:
                return 6;
            case Units:
                return 7;
            case Ounces:
                return 8;
            case OtherOrUnknown:
            default:
                return 0;
        }
    }
    public static int mapUnitMeasureFleet(UnitOfMeasure unitOfMeasure) {
        switch (unitOfMeasure) {
            case Gallons:
                return 1;
            case Liters:
                return 2;
            case Pounds:
                return 3;
            case Kilograms:
                return 4;
            case ImperialGallons:
                return 5;
            case NoFuelPurchased:
            default:
                return 0;
        }
    }


    public static String getTagData16(NtsTag16 ntsTag16) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.padLeft(String.valueOf(ntsTag16.getPumpNumber()), 2, '0')); // Pump Number
        sb.append(StringUtils.padLeft(String.valueOf(ntsTag16.getWorkstationId()), 2, '0')); // Workstation Id
        sb.append(DateTime.now().toString("MMddyy"));
        sb.append(DateTime.now().toString("hhmmss"));
        sb.append(ntsTag16.getServiceCode().getValue()); // Service Code
        sb.append(ntsTag16.getSecurityData().getValue()); // Security Data

        return sb.toString();
    }

    public static StringBuilder getFleetDataTag08(FleetData fleetData, NTSCardTypes ntsCardTypes) {
        StringBuilder sb = new StringBuilder();
        switch (ntsCardTypes) {
            case VisaFleet:
                if (fleetData.getOdometerReading() != null) {
                    sb.append(StringUtils.padLeft(fleetData.getOdometerReading() != null ? fleetData.getOdometerReading() : "", 7, '0'));
                }
                if (fleetData.getDriverId() != null) {
                    sb.append(StringUtils.padRight(fleetData.getDriverId(), 17, ' '));
                } else if (fleetData.getVehicleNumber() != null) {
                    sb.append(StringUtils.padRight(fleetData.getVehicleNumber(), 17, ' '));
                } else if (fleetData.getGenericIdentificationNo() != null) {
                    sb.append(StringUtils.padRight(fleetData.getGenericIdentificationNo(), 17, ' '));
                } else {
                    sb.append(StringUtils.padRight("", 17, ' '));
                }
                return sb;

            case MastercardFleet:
                if (fleetData.getOdometerReading() != null) {
                    sb.append(StringUtils.padLeft(fleetData.getOdometerReading(), 7, '0')); // check in mastercard fleet & visa fleet chapter user data
                } else {
                    sb.append(String.format("%7s", " "));
                }
                if (fleetData.getDriverId() != null) {
                    sb.append(StringUtils.padLeft(fleetData.getDriverId(), 6, '0'));
                } else {
                    sb.append(String.format("%6s", " "));
                }
                if (fleetData.getVehicleNumber() != null) {
                    sb.append(StringUtils.padLeft(fleetData.getVehicleNumber(), 6, '0'));
                } else {
                    sb.append(String.format("%6s", " "));
                }

                return sb;
            default:
                return null;
        }
    }

    public static StringBuilder getProductDataTag09(TransactionBuilder builder, NTSCardTypes ntsCardTypes) {
        StringBuilder sb = new StringBuilder();
        NtsProductData productData = builder.getNtsProductData();
        List<DE63_ProductDataEntry> fuel = productData.getFuelDataEntries();
        List<DE63_ProductDataEntry> nonFuel = productData.getNonFuelDataEntries();
        PurchaseType purchaseType = productData.getPurchaseType();
        ServiceLevel serviceLevelVisaFleet = productData.getServiceLevel();
        boolean fuelFlag= fuel != null;
        int serviceLevel = mapServiceByCardType(productData.getServiceLevel(),ntsCardTypes);
        switch (ntsCardTypes) {
            case VisaFleet:
                sb.append(purchaseType.getValue());
                for (int i = 0; i < 1; i++) {
                    if (fuelFlag && i < fuel.size()) {
                        sb.append(StringUtils.padLeft(fuel.get(i).getCode(), 2, '0'));
                        sb.append(StringUtils.padLeft(purchaseType.equals(PurchaseType.Fuel) ? fuel.get(i).getUnitOfMeasure().getValue() : " ", 1, ' '));
                        sb.append(StringUtils.toNumeric(fuel.get(i).getQuantity(), 6));
                        sb.append(StringUtils.toFormatDigit(fuel.get(i).getPrice(), 5, 3));
                        sb.append(StringUtils.toNumeric(fuel.get(i).getAmount(), 9));
                    } else {
                        sb.append(String.format("%2s", " "));
                        sb.append(String.format("%1s", " "));
                        sb.append(String.format("%06d", 0));
                        sb.append(String.format("%05d", 0));
                        sb.append(String.format("%09d", 0));
                        }
                    }
                sb.append(serviceLevelVisaFleet.getValue());
                sb.append(getRollUpData(builder, ntsCardTypes, productData, 3));
                if (productData.getSalesTax() != null)
                    sb.append(StringUtils.toNumeric(productData.getSalesTax(), 5));
                else
                    sb.append(String.format("%5s", "0"));
                return sb;

            case MastercardFleet:
                sb.append(productData.getProductCodeType().getValue());
                for(int i=0; i<1; i++) {
                    if (fuelFlag && i < fuel.size()) {

                        sb.append(StringUtils.padLeft(fuel.get(i).getCode(), 2, '0'));
                        if (i == 0)
                            sb.append(serviceLevel);
                        sb.append(StringUtils.padLeft(mapUnitMeasureFleet(fuel.get(i).getUnitOfMeasure()), 1, '0'));
                        sb.append(StringUtils.toNumeric(fuel.get(i).getQuantity(), 6));
                        sb.append(StringUtils.toNumeric(fuel.get(i).getPrice(), 5));
                        sb.append(StringUtils.toNumeric(fuel.get(i).getAmount(), 9));
                    } else {
                        sb.append(String.format("%2s", "0"));
                        sb.append("0");
                        sb.append(String.format("%1s", "0"));
                        sb.append(String.format("%6s", "0"));
                        sb.append(String.format("%5s", "0"));
                        sb.append(String.format("%9s", "0"));
                    }
                }
                sb.append(getRollUpData(builder, ntsCardTypes, productData, 3));
                if (productData.getSalesTax() != null)
                    sb.append(StringUtils.toNumeric(productData.getSalesTax(), 5));
                else
                    sb.append(String.format("%5s", "0"));
                return sb;
            case Mastercard:
            case Visa:
            case AmericanExpress:
            case Discover:
            case StoredValueOrHeartlandGiftCard:
            case PinDebit:
                // Preparing product data fuel
                if (fuelFlag && !fuel.isEmpty()) {
                    String code = "";
                    BigDecimal price = new BigDecimal(0),
                            quantity = new BigDecimal(0),
                            amount = new BigDecimal(0);
                    for (int i = 0; i < fuel.size(); i++) {
                        if (fuel.size() > 1) {
                            code = StringUtils.padLeft("99", 3, '0');
                            price = new BigDecimal(0);
                            quantity = quantity.add(fuel.get(i).getQuantity());
                            amount = amount.add(fuel.get(i).getAmount());
                        } else {
                            sb.append(StringUtils.padLeft(fuel.get(i).getCode(), 3, '0'));
                            sb.append(StringUtils.toDecimal(fuel.get(i).getPrice(), 5));
                            sb.append(StringUtils.toDecimal(fuel.get(i).getQuantity(), 7));
                            sb.append(StringUtils.toNumeric(fuel.get(i).getAmount(), 8));
                        }
                    }
                    if (fuel.size() > 1) {
                        sb.append(StringUtils.padLeft(code, 3, '0'));
                        sb.append(StringUtils.toDecimal(price, 5));
                        sb.append(StringUtils.toDecimal(quantity, 7));
                        sb.append(StringUtils.toNumeric(amount, 8));
                    }
                }

                // Preparing product data non-fuel
                int nonFuelProductLimit = fuel.size() >= 1 ? 4 : 5;
                String code = "";
                BigDecimal price = new BigDecimal(0),
                        quantity = new BigDecimal(0),
                        amount = new BigDecimal(0);
                for (int i = 0; i < Math.max(nonFuel.size(), nonFuelProductLimit); i++) {
                    if (nonFuel.size() > nonFuelProductLimit && i >= nonFuelProductLimit - 1) {
                        code = StringUtils.padLeft("400", 3, '0');
                        price = new BigDecimal(0);
                        quantity = quantity.add(nonFuel.get(i).getQuantity());
                        amount = amount.add(nonFuel.get(i).getAmount());
                    } else if (!nonFuel.isEmpty() && i <= nonFuel.size() - 1) {
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 3, '0'));
                        sb.append(StringUtils.toDecimal(nonFuel.get(i).getPrice(), 5));
                        sb.append(StringUtils.toDecimal(nonFuel.get(i).getQuantity(), 7));
                        sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 8));
                    } else {
                        sb.append(String.format("%03d", 0));
                        sb.append(String.format("%05d", 0));
                        sb.append(String.format("%07d", 0));
                        sb.append(String.format("%08d", 0));
                    }
                }
                if (nonFuel.size() > nonFuelProductLimit) {
                    sb.append(StringUtils.padLeft(code, 3, '0'));
                    sb.append(StringUtils.toDecimal(price, 5));
                    sb.append(StringUtils.toDecimal(quantity, 7));
                    sb.append(StringUtils.toNumeric(amount, 8));
                }


                // tax
                if (productData.getSalesTax() != null)
                    sb.append(StringUtils.toNumeric(productData.getSalesTax(), 7));
                else
                    sb.append(String.format("%7d", 0));

                // PDL FUEL DISCOUNT
                if (productData.getDiscount() != null)
                    sb.append(StringUtils.toNumeric(productData.getDiscount(), 5));
                else
                    sb.append(String.format("%5d", 0));

                // Filler
                sb.append(StringUtils.padLeft("", 12, '0'));
                return sb;
            default:
                return null;
        }

    }

    public static String getRequestToBalanceUserData(TransactionBuilder<Transaction> builder) {
        StringBuilder sb = new StringBuilder();
        NtsRequestToBalanceData data = ((ManagementBuilder) builder).getNtsRequestsToBalanceData();
        sb.append(StringUtils.padLeft(data.getDaySequenceNumber(), 3, '0'));
        sb.append(StringUtils.padLeft(StringUtils.toNumeric(data.getPdlBatchDiscount(), 7), 7, '0'));
        sb.append(StringUtils.padRight(data.getVendorSoftwareNumber(), 30, ' '));
        return sb.toString();
    }

    public static String getNonBankCardUserData(TransactionBuilder<Transaction> builder, NTSCardTypes cardType, NtsMessageCode messageCode, AcceptorConfig acceptorConfig) {
        StringBuilder sb = new StringBuilder();
        NtsProductData productData = builder.getNtsProductData();
        BigDecimal salesTax = new BigDecimal(0);
        BigDecimal discount = new BigDecimal(0);
        int serviceLevel = 0;
        String referenceMessageCode = null;
        FleetData fleetData = builder.getFleetData();
        if (productData != null) {
            serviceLevel = mapServiceByCardType(productData.getServiceLevel(),cardType);
            if (productData.getSalesTax() != null)
                salesTax = productData.getSalesTax();
            if (productData.getDiscount() != null)
                discount = productData.getDiscount();
        }
        TransactionType transactionType = builder.getTransactionType();
        if (builder instanceof ManagementBuilder && builder.getPaymentMethod() instanceof TransactionReference) {
            referenceMessageCode = ((TransactionReference) builder.getPaymentMethod()).getOriginalMessageCode();
        }
        switch (cardType) {
            case Mastercard:
            case Visa:
            case AmericanExpress:
            case Discover:
            case StoredValueOrHeartlandGiftCard:
            case PinDebit:
                if (builder.getTransactionType() == TransactionType.DataCollect) {
                    // Data Collect user data for non-fleet bankcards.
                    sb.append(getTagData16(builder.getNtsTag16()));
                    if (acceptorConfig.getAddress() != null) {
                        sb.append(StringUtils.padRight(acceptorConfig.getAddress().getPostalCode(), 9, '0'));
                    } else {
                        sb.append(StringUtils.padRight("", 9, '0'));
                    }
                    if (builder.getCardSequenceNumber() != null) {
                        sb.append(StringUtils.padLeft(builder.getCardSequenceNumber(), 4, '0'));
                    } else {
                        sb.append(StringUtils.padLeft("", 4, '0'));
                    }
                    sb.append(getProductDataTag09(builder, cardType).toString());
                } else {
                    if (!builder.getTransactionType().equals(TransactionType.DataCollect)) {
                        TransactionTypeIndicator indicator = NtsUtils.getTransactionTypeIndicatorForTransaction(builder);
                        sb.append(StringUtils.padRight(indicator.getValue(), 8, ' '));
                        sb.append(StringUtils.padLeft(builder.getSystemTraceAuditNumber(), 6, '0'));
                    }
                }
                break;
            case FuelmanFleet:

            case FleetWide:
                if ((builder.getTransactionType().equals(TransactionType.DataCollect) || builder.getTransactionType().equals(TransactionType.Sale)) && (!messageCode.equals(NtsMessageCode.CreditAdjustment))) {
                    if (fleetData.getDriverId() != null)
                        sb.append(StringUtils.padRight(fleetData.getDriverId(), 5, '0'));
                    if (fleetData.getOdometerReading() != null)
                        sb.append(StringUtils.padRight(fleetData.getOdometerReading(), 6, '0'));
                    sb.append(getFleetCorList(builder,cardType));
                    sb.append(getRollUpData(builder,cardType, productData, 4));

                    sb.append(StringUtils.toNumeric(salesTax, 5));
                } else if (builder.getTransactionType().equals(TransactionType.Auth)) {
                    sb.append(StringUtils.padRight(fleetData.getDriverId(), 5, '0'));
                    sb.append(StringUtils.padRight(fleetData.getOdometerReading(), 6, '0'));
                } else if (messageCode.equals(NtsMessageCode.CreditAdjustment)) {
                    sb.append(getFleetCorCreditAdjustment(builder));
                }
                break;
            case WexFleet:
                if (builder.getTransactionType().equals(TransactionType.Auth)) {
                    sb.append(getWexFleetPromptList(builder));
                    sb.append(StringUtils.padLeft(serviceLevel, 2, '0'));
                    for (DE63_ProductDataEntry entry : productData.getFuelDataEntries()) {
                        sb.append("074");
                        sb.append(StringUtils.toNumeric(entry.getAmount(), 7));
                    }
                    sb.append(StringUtils.padRight(fleetData.getPurchaseDeviceSequenceNumber(), 5, '0'));
                    if (builder.getTagData() != null) {
                        sb.append(builder.getCardSequenceNumber() != null ? builder.getCardSequenceNumber() : "000");
                        sb.append(mapEmvTransactionType(builder.getTransactionModifier()));
                        sb.append(acceptorConfig.getAvailableProductCapability().getValue());
                        sb.append(StringUtils.padLeft(builder.getTagData().length(), 4, '0'));
                        sb.append(builder.getTagData());
                    }
                } else if ((builder.getTransactionType().equals(TransactionType.DataCollect)
                        || builder.getTransactionType().equals(TransactionType.Sale))
                        && !messageCode.equals(NtsMessageCode.CreditAdjustment)) {
                    List<DE63_ProductDataEntry> fuelList = productData.getFuelDataEntries();
                    sb.append(getWexFleetPromptList(builder));
                    if (fuelList != null && !fuelList.isEmpty()) {
                        for (int i = 0; i < fuelList.size(); i++) {
                            if (i == 0) {
                                sb.append(StringUtils.padLeft(mapUnitMeasure(fuelList.get(i).getUnitOfMeasure()), 1, '0'));
                                sb.append(StringUtils.padLeft(serviceLevel, 2, '0'));
                                sb.append(StringUtils.padLeft(fuelList.get(i).getCode(), 3, '0'));
                                sb.append(StringUtils.toFormatDigit(fuelList.get(i).getQuantity(), 7, 3));
                                sb.append(StringUtils.toNumeric(fuelList.get(i).getAmount(), 7));
                            }else if(i==2) {
                                break;
                            }

                            else {
                                sb.append(StringUtils.padLeft(fuelList.get(i).getCode(), 3, '0'));
                                sb.append(StringUtils.padLeft(mapUnitMeasure(fuelList.get(i).getUnitOfMeasure()), 1, '0'));
                                sb.append(StringUtils.toFormatDigit(fuelList.get(i).getQuantity(), 6, 3));
                                sb.append(StringUtils.toNumeric(fuelList.get(i).getAmount(), 6));
                            }
                        }
                    } else {
                        sb.append(String.format("%01d", 0));
                        sb.append(String.format("%02d", 0));
                        sb.append(String.format("%03d", 0));
                        sb.append(String.format("%07d", 0));
                        sb.append(String.format("%07d", 0));
                    }
                    int rollUp=fuelList!=null?fuelList.size()>= 2?6:7:7;
                    sb.append(getRollUpData(builder, cardType, productData, rollUp));
                    sb.append(fleetData.getPurchaseDeviceSequenceNumber());
                    sb.append(StringUtils.toNumeric(salesTax, 5));
                    sb.append(StringUtils.toNumeric(discount, 5));
                    if (builder.getTagData() != null) {
                        sb.append(builder.getCardSequenceNumber() != null ? builder.getCardSequenceNumber() : "000");
                        sb.append(mapEmvTransactionType(builder.getTransactionModifier()));
                        sb.append(StringUtils.padLeft(builder.getTagData().length(), 4, '0'));
                        sb.append(builder.getTagData());
                    }
                } else if (messageCode.equals(NtsMessageCode.CreditAdjustment)) {
                    sb.append(StringUtils.padLeft(fleetData.getPurchaseDeviceSequenceNumber(), 5, '0'));
                    if (fleetData.getDriverId() != null)
                        sb.append(StringUtils.padRight(fleetData.getDriverId(), 6, ' '));
                    else
                        sb.append(StringUtils.padRight("", 6, '0'));
                    sb.append(StringUtils.padLeft(builder.getNtsDataCollectRequest().getBatchNumber(), 2, '0'));
                    sb.append(StringUtils.padLeft(builder.getNtsDataCollectRequest().getSequenceNumber(), 3, '0'));
                    sb.append(builder.getNtsDataCollectRequest().getOriginalTransactionDate());
                } else if (referenceMessageCode != null && referenceMessageCode.equals("01")
                        && builder.getTransactionType().equals(TransactionType.Reversal)) {

                    sb.append(StringUtils.padLeft(serviceLevel, 2, '0'));
                    for (DE63_ProductDataEntry entry : productData.getFuelDataEntries()) {
                        sb.append("074");
                        sb.append(StringUtils.toNumeric(entry.getAmount(), 7));
                    }
                    sb.append(StringUtils.padRight(fleetData.getPurchaseDeviceSequenceNumber(), 5, '0'));
                } else if (referenceMessageCode != null && referenceMessageCode.equals("02")
                        && transactionType.equals(TransactionType.Reversal)) {
                    List<DE63_ProductDataEntry> fuelList = productData.getFuelDataEntries();
                    if (fuelList != null && !fuelList.isEmpty()) {
                        for (int i = 0; i < fuelList.size(); i++) {
                            if (i == 0) {
                                sb.append(StringUtils.padLeft(mapUnitMeasure(fuelList.get(i).getUnitOfMeasure()), 1, '0'));
                                sb.append(StringUtils.padLeft(serviceLevel, 2, '0'));
                                sb.append(StringUtils.padLeft(fuelList.get(i).getCode(), 3, '0'));
                                sb.append(StringUtils.toFormatDigit(fuelList.get(i).getQuantity(), 7, 3));
                                sb.append(StringUtils.toNumeric(fuelList.get(i).getAmount(), 7));
                            } else {
                                sb.append(StringUtils.padLeft(fuelList.get(i).getCode(), 3, '0'));
                                sb.append(StringUtils.padLeft(mapUnitMeasure(fuelList.get(i).getUnitOfMeasure()), 1, '0'));
                                sb.append(StringUtils.toFormatDigit(fuelList.get(i).getQuantity(), 6, 3));
                                sb.append(StringUtils.toNumeric(fuelList.get(i).getAmount(), 6));
                            }
                        }
                    } else {
                        sb.append(String.format("%01d", 0));
                        sb.append(String.format("%02d", 0));
                        sb.append(String.format("%03d", 0));
                        sb.append(String.format("%07d", 0));
                        sb.append(String.format("%07d", 0));
                    }
                    sb.append(getRollUpData(builder, cardType, productData, 7));
                    sb.append(fleetData.getPurchaseDeviceSequenceNumber());
                    sb.append(StringUtils.toNumeric(salesTax, 5));
                    sb.append(StringUtils.toNumeric(discount, 5));
                }
                break;
            case VoyagerFleet:
                if (builder.getTransactionType().equals(TransactionType.Auth)) {
                    if (fleetData.getOdometerReading() != null)
                        sb.append(StringUtils.padRight(fleetData.getOdometerReading(), 7, '0'));
                    if (fleetData.getVehicleNumber() != null)
                        sb.append(StringUtils.padRight(fleetData.getVehicleNumber(), 6, '0'));
                } else if (builder.getTransactionType().equals(TransactionType.DataCollect)
                        || builder.getTransactionType().equals(TransactionType.Sale)) {
                    if (messageCode.equals(NtsMessageCode.DataCollectOrSale)) {
                        if (fleetData.getOdometerReading() != null)
                            sb.append(StringUtils.padRight(fleetData.getOdometerReading(), 7, '0'));
                        if (fleetData.getVehicleNumber() != null)
                            sb.append(StringUtils.padRight(fleetData.getVehicleNumber(), 6, '0'));
                        sb.append(serviceLevel);
                        sb.append(getVoyagerFleetFuelList(builder));
                        sb.append(getRollUpData(builder, cardType, productData, 4));
                        sb.append(StringUtils.toNumeric(salesTax, 6));
                    } else if (messageCode.equals(NtsMessageCode.CreditAdjustment)) {

                        sb.append(builder.getInvoiceNumber());
                        sb.append(serviceLevel);
                        sb.append(getVoyagerFleetFuelList(builder));
                        sb.append(getRollUpData(builder, cardType, productData, 4));
                        sb.append(StringUtils.toNumeric(salesTax, 6));
                    }
                }
                break;
            default:
                break;
        }
        return sb.toString();
    }


    private static StringBuilder getRollUpData(TransactionBuilder builder, NTSCardTypes cardType, NtsProductData productData, int rollUpAt) {
        StringBuilder sb = new StringBuilder();
        TransactionType transactionType = builder.getTransactionType();
        List<DE63_ProductDataEntry> nonFuel = productData.getNonFuelDataEntries();
        int nonFuelSize = nonFuel.size();
        float sumAmount = 0.0f;
        if (cardType.equals(NTSCardTypes.VisaFleet) || cardType.equals(NTSCardTypes.MastercardFleet)) {
            if (nonFuelSize >= rollUpAt) {
                for (int i = 0; i < nonFuelSize; i++) {
                    if (i < rollUpAt - 1) {
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 2, ' '));
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 2, '0'));
                        sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 6));
                    } else {
                        sumAmount += nonFuel.get(i).getAmount().floatValue();
                    }
                }
                if (cardType.equals(NTSCardTypes.VisaFleet))
                    sb.append(StringUtils.padLeft(90, 2, ' '));
                else
                    sb.append(StringUtils.padLeft(99, 2, ' '));
                sb.append(StringUtils.padLeft(nonFuelSize - rollUpAt + 1, 2, '0'));
                sb.append(StringUtils.toNumeric(BigDecimal.valueOf(sumAmount), 6));
            } else {
                for (int i = 0; i < rollUpAt; i++) {
                    if (i < nonFuelSize) {
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 2, ' '));
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 2, '0'));
                        sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 6));
                    } else {
                        sb.append(String.format("%2s", " "));
                        sb.append(String.format("%02d", 0));
                        sb.append(String.format("%06d", 0));
                    }
                }
            }
        } else if (cardType.equals(NTSCardTypes.WexFleet)) {
            if (transactionType.equals(TransactionType.Reversal)) {
                int x = productData.getFuelDataEntries().size() == 2 ? 1 : 0;
                rollUpAt = rollUpAt - x;
                if (nonFuelSize > rollUpAt) {
                    for (int i = 0; i < nonFuelSize; i++) {
                        if (i < rollUpAt - 1) {
                            sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 3, '0'));
                            sb.append(StringUtils.padLeft(mapUnitMeasure(nonFuel.get(i).getUnitOfMeasure()), 1, '0'));
                            if (i == 0 - x) {
                                sb.append(StringUtils.toFormatDigit(nonFuel.get(i).getQuantity(), 6, 3));
                            } else {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 3, '0'));
                            }
                            sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 6));
                        } else {
                            sumAmount += nonFuel.get(i).getAmount().floatValue();
                        }
                    }
                    sb.append(StringUtils.padLeft(400, 3, '0'));
                    sb.append(String.format("%01d", 0));
                    sb.append(StringUtils.padLeft(1, 1, '0'));
                    sb.append(StringUtils.toNumeric(BigDecimal.valueOf(sumAmount), 6));
                } else {
                    for (int i = 0; i < rollUpAt; i++) {
                        if (i < nonFuelSize) {
                            sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 3, '0'));
                            sb.append(StringUtils.padLeft(mapUnitMeasure(nonFuel.get(i).getUnitOfMeasure()), 1, '0'));
                            if (i == 0 - x) {
                                sb.append(StringUtils.toFormatDigit(nonFuel.get(i).getQuantity(), 6, 3));
                            } else {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 3, '0'));
                            }
                            sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 6));
                        } else {
                            sb.append(String.format("%03d", 0));
                            sb.append(String.format("%01d", 0));
                            if (i == 0 - x) {
                                sb.append(String.format("%06d", 0));
                            } else {
                                sb.append(String.format("%03d", 0));
                            }
                            sb.append(String.format("%06d", 0));
                        }
                    }
                }
            } else {
                int x = productData.getFuelDataEntries().size() == 2 ? 1 : 0;
                rollUpAt = rollUpAt - x;
                if (nonFuelSize > rollUpAt) {
                    for (int i = 0; i < nonFuelSize; i++) {
                        if (i < rollUpAt - 1) {
                            sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 3, '0'));
                            sb.append(StringUtils.padLeft(mapUnitMeasure(nonFuel.get(i).getUnitOfMeasure()), 1, '0'));
                            if (i == 0 - x) {
                                sb.append(StringUtils.toFormatDigit(nonFuel.get(i).getQuantity(), 6, 3));
                            } else if (i == 1 - x || i == 2 - x || i == 3 - x) {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 3, '0'));
                            } else if (i == 4 - x) {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 2, '0'));
                            } else {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 1, '0'));
                            }
                            sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 6));
                        } else {
                            sumAmount += nonFuel.get(i).getAmount().floatValue();
                        }
                    }
                    sb.append(StringUtils.padLeft(400, 3, '0'));
                    sb.append(String.format("%01d", 0));
                    sb.append(StringUtils.padLeft(1, 1, '0'));
                    sb.append(StringUtils.toNumeric(BigDecimal.valueOf(sumAmount), 6));
                } else {
                    for (int i = 0; i < rollUpAt; i++) {
                        if (i < nonFuelSize) {
                            sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 3, '0'));
                            sb.append(StringUtils.padLeft(mapUnitMeasure(nonFuel.get(i).getUnitOfMeasure()), 1, '0'));
                            if (i == 0 - x) {
                                sb.append(StringUtils.toFormatDigit(nonFuel.get(i).getQuantity(), 6, 3));
                            } else if (i == 1 - x || i == 2 - x || i == 3 - x) {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 3, '0'));
                            } else if (i == 4 - x) {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 2, '0'));
                            } else {
                                sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 1, '0'));
                            }
                            sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 6));
                        } else {
                            sb.append(String.format("%03d", 0));
                            sb.append(String.format("%01d", 0));
                            if (i == 0 - x) {
                                sb.append(String.format("%06d", 0));
                            } else if (i == 1 - x || i == 2 - x || i == 3 - x) {
                                sb.append(String.format("%03d", 0));
                            } else if (i == 4 - x) {
                                sb.append(String.format("%02d", 0));
                            } else {
                                sb.append(String.format("%01d", 0));
                            }
                            sb.append(String.format("%06d", 0));
                        }
                    }
                }
            }

        } else if (cardType.equals(NTSCardTypes.FuelmanFleet) || cardType.equals(NTSCardTypes.FleetWide)) {
            if (nonFuelSize >= 4) {
                for (int index = 0; index < nonFuelSize; index++) {
                    if (index < rollUpAt - 1) {
                        sb.append(StringUtils.padLeft(nonFuel.get(index).getCode(), 3, ' '));
                        sb.append(StringUtils.padLeft(nonFuel.get(index).getQuantity().intValue(), 4, '0'));
                        sb.append(StringUtils.toNumeric(nonFuel.get(index).getAmount(), 5));
                    } else {
                        sumAmount += nonFuel.get(index).getAmount().floatValue();
                    }
                }
                sb.append(StringUtils.padLeft(400, 3, ' '));
                sb.append(StringUtils.padLeft(0001, 4, '0'));
                sb.append(StringUtils.toNumeric(BigDecimal.valueOf(sumAmount), 5));
            } else {
                for (int index = 0; index < rollUpAt; index++) {
                    if (index < nonFuelSize) {
                        sb.append(StringUtils.padLeft(nonFuel.get(index).getCode(), 3, ' '));
                        sb.append(StringUtils.padLeft(nonFuel.get(index).getQuantity().intValue(), 4, '0'));
                        sb.append(StringUtils.toNumeric(nonFuel.get(index).getAmount(), 5));
                    } else {
                        sb.append(String.format("%3s", " "));
                        sb.append(String.format("%04d", 0));
                        sb.append(String.format("%05d", 0));
                    }
                }
            }
        } else if (cardType.equals(NTSCardTypes.VoyagerFleet)) {
            sumAmount = 0.0f;
            if (nonFuelSize > rollUpAt) {
                for (int i = 0; i < nonFuelSize; i++) {
                    if (i < rollUpAt - 1) {
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 2, ' '));
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 2, '0'));
                        sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 5));
                    } else {
                        sumAmount += nonFuel.get(i).getAmount().floatValue();
                    }
                }
                sb.append(StringUtils.padLeft(33, 2, ' '));
                sb.append("01");
                sb.append(StringUtils.toNumeric(BigDecimal.valueOf(sumAmount), 5));
            } else {
                for (int i = 0; i < rollUpAt; i++) {
                    if (i < nonFuelSize) {
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getCode(), 2, ' '));
                        sb.append(StringUtils.padLeft(nonFuel.get(i).getQuantity().intValue(), 2, '0'));
                        sb.append(StringUtils.toNumeric(nonFuel.get(i).getAmount(), 5));
                    } else {
                        sb.append(String.format("%2s", " "));
                        sb.append(String.format("%02d", 0));
                        sb.append(String.format("%05d", 0));
                    }
                }
            }
        }
        return sb;
    }

    private static String mapEmvTransactionType(TransactionModifier transTypeIndicator) {
        switch (transTypeIndicator) {
            case Fallback:
                return "F";
            case Offline:
                return "A";
            case ChipDecline:
                return "D";
            default:
                return " ";
        }
    }

    private static StringBuffer getFleetCorCreditAdjustment(TransactionBuilder<Transaction> builder) {
        StringBuffer sb = new StringBuffer();
        NtsDataCollectRequest getNtsDataCollect = builder.getNtsDataCollectRequest();
        sb.append(StringUtils.padLeft(getNtsDataCollect.getApprovalCode(), 6, '0'));
        sb.append(StringUtils.padLeft(getNtsDataCollect.getBatchNumber(), 2, '0'));
        sb.append(StringUtils.padLeft(getNtsDataCollect.getSequenceNumber(), 3, '0'));
        sb.append(DateTime.now().toString("yyMMdd"));
        sb.append(DateTime.now().toString("HHmmss"));
        return sb;
    }
    private static StringBuffer getWexFleetPromptList(TransactionBuilder<Transaction> builder) {
        StringBuffer sb = new StringBuffer();
        LinkedHashMap<ProductCodeType, String> promptList = null;
        NtsProductData productData = builder.getNtsProductData();

        if (productData != null) {
            promptList = productData.getPromptList();
        }
        int promptSize=0;
        if(promptList!=null) {
            promptSize = builder.getTagData() != null ?
                    Math.min(promptList.size(), 6) :
                    Math.min(promptList.size(), 3);
            sb.append(promptSize);
            promptList.entrySet().stream().limit(promptSize).forEach(list -> {
                sb.append(list.getKey().getValue());
                int length = list.getValue().length();
                sb.append(StringUtils.padLeft(length, 2, '0'));
                sb.append(StringUtils.padLeft(list.getValue(), 12, '0'));
            });
        }
        int remainingPrompt=builder.getTagData()!=null?
                Math.max(promptSize, 6) :
                Math.max(promptSize, 3);
        for (int i = promptSize; i < remainingPrompt; i++) {
            sb.append(String.format("%01d", 0));
            sb.append(String.format("%02d", 0));
            sb.append(String.format("%012d", 0));
        }
        return sb;
    }
    private static StringBuffer getVoyagerFleetFuelList(TransactionBuilder<Transaction> builder) {
        StringBuffer sb = new StringBuffer();
        NtsProductData productData = builder.getNtsProductData();
        List<DE63_ProductDataEntry> fuelList = productData.getFuelDataEntries();
        for (int i = 0; i <2; i++) {
            if(fuelList!=null && i< fuelList.size()) {

                sb.append(StringUtils.padLeft(fuelList.get(i).getCode(), 2, '0'));
                sb.append(StringUtils.toNumeric(fuelList.get(i).getQuantity(), 5));
                sb.append(StringUtils.toNumeric(fuelList.get(i).getAmount(), 5));
            } else {
                sb.append(String.format("%02d", 0));
                sb.append(String.format("%05d", 0));
                sb.append(String.format("%05d", 0));
            }
        }
        return sb;
    }
    private static StringBuffer getFleetCorList(TransactionBuilder<Transaction> builder,NTSCardTypes ntsCardTypes){
        StringBuffer sb = new StringBuffer();
        NtsProductData productData = builder.getNtsProductData();
        List<DE63_ProductDataEntry> fuelList = productData.getFuelDataEntries();
        int serviceLevel = mapServiceByCardType(productData.getServiceLevel(),ntsCardTypes);
        for (int i = 0; i <1; i++) {
            if(fuelList!=null && i< fuelList.size()) {
                sb.append(mapUnitMeasureFleet(fuelList.get(i).getUnitOfMeasure()));
                sb.append(serviceLevel);
                sb.append(StringUtils.padLeft(fuelList.get(i).getCode(), 3, ' '));
                sb.append(StringUtils.toNumeric(fuelList.get(i).getPrice(), 5));
                sb.append(StringUtils.toNumeric(fuelList.get(i).getQuantity(), 6));
                sb.append(StringUtils.toNumeric(fuelList.get(i).getAmount(), 5));
                            }
            else {
                sb.append(String.format("%1s", "0"));
                sb.append(serviceLevel);
                sb.append(String.format("%3s", " "));
                sb.append(String.format("%05d", 0));
                sb.append(String.format("%06d", 0));
                sb.append(String.format("%05d", 0));
            }
        }
        return sb;
    }
}