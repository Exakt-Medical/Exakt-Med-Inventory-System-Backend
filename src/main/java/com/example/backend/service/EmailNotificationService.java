package com.example.backend.service;

import com.example.backend.dto.order.OrderItemResponseDto;
import com.example.backend.dto.order.OrderResponseDto;
import com.example.backend.dto.reorderQuotation.ReorderQuotationItemResponseDto;
import com.example.backend.dto.reorderQuotation.ReorderQuotationResponseDto;
import com.example.backend.entity.Order;
import com.example.backend.entity.ReorderQuotation;
import com.example.backend.repository.UserPharmacyLinkRepository;
import com.example.backend.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final UserPharmacyLinkRepository userPharmacyLinkRepository;
    private final UserRepository userRepository;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EmailNotificationService(JavaMailSender mailSender,
                                    UserPharmacyLinkRepository userPharmacyLinkRepository,
                                    UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userPharmacyLinkRepository = userPharmacyLinkRepository;
        this.userRepository = userRepository;
    }

    /**
     * Sends an asynchronous HTML email notification to all Procurement and Admin users
     * assigned to the quotation's operating pharmacy using the detached response DTO.
     */
    @Async
    public void notifyProcurementOfNewQuotation(ReorderQuotationResponseDto quotation, String facilityName) {
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.mail.enabled=false). Skipping email for quotation: {}",
                    quotation != null ? quotation.getQuotationNumber() : "null");
            return;
        }

        if (quotation == null || quotation.getPharmacyId() == null) {
            log.warn("Cannot send quotation email: quotation or pharmacyId is null.");
            return;
        }

        try {
            Long pharmacyId = quotation.getPharmacyId();

            // Find all assigned Procurement user emails directly via JPQL
            List<String> recipientEmails = userPharmacyLinkRepository.findProcurementEmailsByPharmacyId(pharmacyId);

            if (recipientEmails.isEmpty()) {
                log.warn("No assigned Procurement users with an email address found for pharmacy ID {} ('{}').",
                        pharmacyId, quotation.getPharmacyName());
                return;
            }

            // Build email subject and HTML message
            String subject = String.format("[Exakt Med] New Reorder Quotation Request: %s (%s)",
                    quotation.getQuotationNumber(), quotation.getPharmacyName());

            String htmlBody = buildQuotationHtmlTemplate(quotation, facilityName);

            // Dispatch MimeMessage via SMTP
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String senderAddress = (mailFrom != null && !mailFrom.trim().isBlank()) ? mailFrom.trim() : "noreply@exaktmed.com";
            helper.setFrom(senderAddress, "Exakt Med Inventory System");
            helper.setTo(recipientEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched quotation email alert for {} to {} recipient(s): {}",
                    quotation.getQuotationNumber(), recipientEmails.size(), recipientEmails);

        } catch (Exception ex) {
            log.error("Failed to send quotation alert email for quotation {}: {}",
                    quotation.getQuotationNumber(), ex.getMessage(), ex);
        }
    }

    private String buildQuotationHtmlTemplate(ReorderQuotationResponseDto q, String facilityName) {
        StringBuilder itemsHtml = new StringBuilder();
        long totalRequestedUnits = 0L;
        if (q.getItems() != null) {
            for (ReorderQuotationItemResponseDto item : q.getItems()) {
                String skuName = item.getSkuCode() != null ? item.getSkuCode() : (item.getGenericName() != null ? item.getGenericName() : "—");
                String brandName = item.getBrandName() != null ? item.getBrandName() : "—";
                String packaging = item.getPackagingUnit() != null ? item.getPackagingUnit() : "Units";
                long units = item.getRequestedUnits() != null ? item.getRequestedUnits() : 0L;
                totalRequestedUnits += units;

                itemsHtml.append(String.format(
                        "<tr>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb;'><strong>%s</strong></td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #4b5563;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #64748b;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: center; font-weight: 600;'>%,d</td>" +
                        "</tr>",
                        skuName, brandName, packaging, units
                ));
            }
        }

        String creatorName = q.getUserName() != null && !q.getUserName().isBlank() ? q.getUserName() : "Pharmacist";
        String resolvedFacility = facilityName != null && !facilityName.isBlank() ? facilityName : "—";
        String pharmacyName = q.getPharmacyName() != null ? q.getPharmacyName() : "—";
        String supplierName = q.getSupplierName() != null && !q.getSupplierName().isBlank() ? q.getSupplierName() : "—";
        String notes = q.getNotes() != null && !q.getNotes().isBlank() ? q.getNotes().trim() : "None provided";
        int itemCount = q.getItems() != null ? q.getItems().size() : 0;

        String reviewUrl = String.format("%s/procurement/order-request", frontendUrl);

        return String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b;'>" +
                "  <div style='max-width: 620px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);'>" +
                "    <div style='background: linear-gradient(135deg, #2563eb, #1d4ed8); padding: 24px; color: #ffffff;'>" +
                "      <span style='font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; opacity: 0.9;'>Exakt Med Procurement Notification</span>" +
                "      <h1 style='margin: 6px 0 0; font-size: 22px; font-weight: 700;'>New Reorder Quotation Request</h1>" +
                "    </div>" +
                "    <div style='padding: 24px;'>" +
                "      <p style='margin-top: 0; font-size: 14px; line-height: 1.5; color: #475569;'>" +
                "        A pharmacist has submitted a new reorder quotation for procurement review and purchase order generation." +
                "      </p>" +
                "      <div style='background-color: #f1f5f9; border-radius: 8px; padding: 14px 18px; margin: 18px 0; font-size: 13px; line-height: 1.6;'>" +
                "        <table style='width: 100%%; border-collapse: collapse;'>" +
                "          <tr><td style='color: #64748b; width: 35%%;'>Quotation No:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Facility:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Pharmacy:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Preferred Supplier:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Requested By:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Total Line Items:</td><td><strong>%d item(s) (%,d units)</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Pharmacist Notes:</td><td style='color: #334155; font-style: italic;'>%s</td></tr>" +
                "        </table>" +
                "      </div>" +
                "      <h3 style='font-size: 14px; font-weight: 700; margin: 20px 0 8px; color: #1e293b;'>Requested Items</h3>" +
                "      <table style='width: 100%%; border-collapse: collapse; font-size: 12px; margin-bottom: 24px;'>" +
                "        <thead>" +
                "          <tr style='background-color: #f8fafc; border-bottom: 2px solid #cbd5e1; text-align: left; color: #64748b; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em;'>" +
                "            <th style='padding: 8px 12px;'>SKU / Description</th>" +
                "            <th style='padding: 8px 12px;'>Brand</th>" +
                "            <th style='padding: 8px 12px;'>Packaging</th>" +
                "            <th style='padding: 8px 12px; text-align: center;'>Requested Qty</th>" +
                "          </tr>" +
                "        </thead>" +
                "        <tbody>%s</tbody>" +
                "      </table>" +
                "      <div style='text-align: center; margin: 28px 0 12px;'>" +
                "        <a href='%s' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 12px 28px; font-size: 14px; font-weight: 600; border-radius: 8px; box-shadow: 0 2px 4px rgba(37,99,235,0.25);'>Review & Price in Exakt Med</a>" +
                "      </div>" +
                "    </div>" +
                "    <div style='background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 14px 24px; font-size: 11px; color: #94a3b8; text-align: center;'>" +
                "      This is an automated operational alert generated by Exakt Med Inventory System. Please do not reply directly to this email." +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>",
                q.getQuotationNumber(),
                resolvedFacility,
                pharmacyName,
                supplierName,
                creatorName,
                itemCount,
                totalRequestedUnits,
                notes,
                itemsHtml.toString(),
                reviewUrl
        );
    }

    /**
     * Sends an asynchronous HTML email notification to all Admin users
     * assigned to the operating pharmacy when a Procurement Officer creates a Purchase Order.
     */
    @Async
    public void notifyAdminOfNewPurchaseOrder(OrderResponseDto order, String facilityName, String creatorName) {
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.mail.enabled=false). Skipping email for PO: {}",
                    order != null ? order.getPurchaseOrderNum() : "null");
            return;
        }

        if (order == null || order.getPharmacyId() == null) {
            log.warn("Cannot send PO email: order or pharmacyId is null.");
            return;
        }

        try {
            Long pharmacyId = order.getPharmacyId();

            // Find all Admin user emails assigned to this pharmacy
            List<String> adminEmails = userPharmacyLinkRepository.findAdminEmailsByPharmacyId(pharmacyId);

            if (adminEmails.isEmpty()) {
                log.warn("No assigned Admin users with an email address found for pharmacy ID {} ('{}').",
                        pharmacyId, order.getPharmacyName());
                return;
            }

            // Build email subject and HTML message
            String subject = String.format("[Exakt Med] New Purchase Order Created: %s (%s)",
                    order.getPurchaseOrderNum(), order.getPharmacyName());

            String htmlBody = buildPurchaseOrderHtmlTemplate(order, facilityName, creatorName);

            // Dispatch MimeMessage via SMTP
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String senderAddress = (mailFrom != null && !mailFrom.trim().isBlank()) ? mailFrom.trim() : "noreply@exaktmed.com";
            helper.setFrom(senderAddress, "Exakt Med Inventory System");
            helper.setTo(adminEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched PO creation email alert for {} to {} Admin recipient(s): {}",
                    order.getPurchaseOrderNum(), adminEmails.size(), adminEmails);

        } catch (Exception ex) {
            log.error("Failed to send PO creation alert email for PO {}: {}",
                    order.getPurchaseOrderNum(), ex.getMessage(), ex);
        }
    }

    private String buildPurchaseOrderHtmlTemplate(OrderResponseDto o, String facilityName, String creatorName) {
        StringBuilder itemsHtml = new StringBuilder();
        if (o.getItems() != null) {
            for (OrderItemResponseDto item : o.getItems()) {
                String skuName = item.getSkuName() != null ? item.getSkuName() : (item.getGenericName() != null ? item.getGenericName() : "—");
                String brandName = item.getBrandName() != null ? item.getBrandName() : "—";
                String packaging = item.getPackagingUnit() != null ? item.getPackagingUnit() : "Units";
                long units = item.getOrderedUnits() != null ? item.getOrderedUnits() : 0L;
                long price = item.getPrice() != null ? item.getPrice() : 0L;
                double pricePerUnit = item.getPricePerUnit() != null ? item.getPricePerUnit() : (units > 0 ? (double) price / units : 0.0);

                itemsHtml.append(String.format(
                        "<tr>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb;'><strong>%s</strong></td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #4b5563;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #64748b;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: center; font-weight: 600;'>%,d</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: right;'>₱%,.2f</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>₱%,d</td>" +
                        "</tr>",
                        skuName, brandName, packaging, units, pricePerUnit, price
                ));
            }
        }

        String resolvedCreator = creatorName != null && !creatorName.isBlank() ? creatorName : "Procurement Officer";
        String resolvedFacility = facilityName != null && !facilityName.isBlank() ? facilityName : "—";
        String pharmacyName = o.getPharmacyName() != null ? o.getPharmacyName() : "—";
        String supplierName = o.getSupplierName() != null && !o.getSupplierName().isBlank() ? o.getSupplierName() : "—";
        long totalCost = o.getTotalPrice() != null ? o.getTotalPrice() : 0L;
        long totalUnits = o.getTotalOrderedUnits() != null ? o.getTotalOrderedUnits() : 0L;
        String notes = o.getNotes() != null && !o.getNotes().isBlank() ? o.getNotes().trim() : "None provided";
        String priority = o.getPriority() != null ? o.getPriority() : "Normal";
        int itemCount = o.getItems() != null ? o.getItems().size() : 0;

        String reviewUrl = String.format("%s/admin/purchase-orders", frontendUrl);

        return String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b;'>" +
                "  <div style='max-width: 640px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);'>" +
                "    <div style='background: linear-gradient(135deg, #059669, #047857); padding: 24px; color: #ffffff;'>" +
                "      <span style='font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; opacity: 0.9;'>Exakt Med Administration Alert</span>" +
                "      <h1 style='margin: 6px 0 0; font-size: 22px; font-weight: 700;'>New Purchase Order Created</h1>" +
                "    </div>" +
                "    <div style='padding: 24px;'>" +
                "      <p style='margin-top: 0; font-size: 14px; line-height: 1.5; color: #475569;'>" +
                "        A procurement officer has generated a new Purchase Order requisition for approval and vendor fulfillment." +
                "      </p>" +
                "      <div style='background-color: #f1f5f9; border-radius: 8px; padding: 14px 18px; margin: 18px 0; font-size: 13px; line-height: 1.6;'>" +
                "        <table style='width: 100%%; border-collapse: collapse;'>" +
                "          <tr><td style='color: #64748b; width: 35%%;'>Purchase Order #:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Facility:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Pharmacy:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Vendor / Supplier:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Created By:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Priority:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Total Units:</td><td><strong>%d item(s) (%,d units)</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Total PO Amount:</td><td style='color: #059669; font-weight: 700; font-size: 15px;'>₱%,d</td></tr>" +
                "          <tr><td style='color: #64748b;'>Procurement Notes:</td><td style='color: #334155; font-style: italic;'>%s</td></tr>" +
                "        </table>" +
                "      </div>" +
                "      <h3 style='font-size: 14px; font-weight: 700; margin: 20px 0 8px; color: #1e293b;'>Order Line Items</h3>" +
                "      <table style='width: 100%%; border-collapse: collapse; font-size: 12px; margin-bottom: 24px;'>" +
                "        <thead>" +
                "          <tr style='background-color: #f8fafc; border-bottom: 2px solid #cbd5e1; text-align: left; color: #64748b; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em;'>" +
                "            <th style='padding: 8px 12px;'>SKU / Description</th>" +
                "            <th style='padding: 8px 12px;'>Brand</th>" +
                "            <th style='padding: 8px 12px;'>Packaging</th>" +
                "            <th style='padding: 8px 12px; text-align: center;'>Qty</th>" +
                "            <th style='padding: 8px 12px; text-align: right;'>Unit Price</th>" +
                "            <th style='padding: 8px 12px; text-align: right;'>Subtotal</th>" +
                "          </tr>" +
                "        </thead>" +
                "        <tbody>%s</tbody>" +
                "      </table>" +
                "      <div style='text-align: center; margin: 28px 0 12px;'>" +
                "        <a href='%s' style='display: inline-block; background-color: #059669; color: #ffffff; text-decoration: none; padding: 12px 28px; font-size: 14px; font-weight: 600; border-radius: 8px; box-shadow: 0 2px 4px rgba(5,150,105,0.25);'>Review Purchase Order in Exakt Med</a>" +
                "      </div>" +
                "    </div>" +
                "    <div style='background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 14px 24px; font-size: 11px; color: #94a3b8; text-align: center;'>" +
                "      This is an automated operational alert generated by Exakt Med Inventory System. Please do not reply directly to this email." +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>",
                o.getPurchaseOrderNum(),
                resolvedFacility,
                pharmacyName,
                supplierName,
                resolvedCreator,
                priority,
                itemCount,
                totalUnits,
                totalCost,
                notes,
                itemsHtml.toString(),
                reviewUrl
        );
    }

    /**
     * Sends an asynchronous HTML email notification to all Procurement users assigned
     * to the operating pharmacy when an Administrator Approves or Denies a Purchase Order.
     */
    @Async
    public void notifyProcurementOfOrderStatusUpdate(OrderResponseDto order,
                                                     Order.Status status,
                                                     String notes,
                                                     String facilityName,
                                                     String reviewerName) {
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.mail.enabled=false). Skipping PO status email for PO: {}",
                    order != null ? order.getPurchaseOrderNum() : "null");
            return;
        }

        if (order == null || order.getPharmacyId() == null) {
            log.warn("Cannot send PO status email: order or pharmacyId is null.");
            return;
        }

        try {
            Long pharmacyId = order.getPharmacyId();

            // Find all Procurement user emails assigned to this pharmacy
            List<String> procurementEmails = userPharmacyLinkRepository.findProcurementEmailsByPharmacyId(pharmacyId);

            if (procurementEmails.isEmpty()) {
                log.warn("No assigned Procurement users with an email address found for pharmacy ID {} ('{}').",
                        pharmacyId, order.getPharmacyName());
                return;
            }

            boolean isApproved = (status == Order.Status.Approved);
            String statusLabel = isApproved ? "APPROVED" : "DENIED";

            // Build email subject and HTML message
            String subject = String.format("[Exakt Med] Purchase Order %s: %s (%s)",
                    statusLabel, order.getPurchaseOrderNum(), order.getPharmacyName());

            String htmlBody = buildOrderStatusUpdateHtmlTemplate(order, status, notes, facilityName, reviewerName);

            // Dispatch MimeMessage via SMTP
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String senderAddress = (mailFrom != null && !mailFrom.trim().isBlank()) ? mailFrom.trim() : "noreply@exaktmed.com";
            helper.setFrom(senderAddress, "Exakt Med Inventory System");
            helper.setTo(procurementEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched PO status update ({}) email for {} to {} Procurement recipient(s): {}",
                    statusLabel, order.getPurchaseOrderNum(), procurementEmails.size(), procurementEmails);

        } catch (Exception ex) {
            log.error("Failed to send PO status update email for PO {}: {}",
                    order.getPurchaseOrderNum(), ex.getMessage(), ex);
        }
    }

    private String buildOrderStatusUpdateHtmlTemplate(OrderResponseDto o,
                                                      Order.Status status,
                                                      String adminNotes,
                                                      String facilityName,
                                                      String reviewerName) {
        boolean isApproved = (status == Order.Status.Approved);
        String headerGradient = isApproved
                ? "linear-gradient(135deg, #059669, #047857)"   // Emerald Green
                : "linear-gradient(135deg, #dc2626, #b91c1c)";  // Crimson Red
        String statusLabel = isApproved ? "APPROVED" : "DENIED";
        String statusBgColor = isApproved ? "#dcfce7" : "#fee2e2";
        String statusTextColor = isApproved ? "#15803d" : "#b91c1c";
        String buttonColor = isApproved ? "#059669" : "#475569";

        String actionCallout;
        if (isApproved) {
            actionCallout =
                "<div style='background-color: #ecfdf5; border-left: 4px solid #10b981; border-radius: 6px; padding: 14px 18px; margin: 18px 0;'>" +
                "  <strong style='color: #065f46; font-size: 14px;'>Action Required: Proceed with Ordering</strong>" +
                "  <p style='margin: 6px 0 0; color: #047857; font-size: 13px; line-height: 1.5;'>" +
                "    This purchase order has been approved by the Administration. <strong>Please proceed with ordering and placing the purchase order with the supplier.</strong>" +
                "  </p>" +
                "</div>";
        } else {
            String reasonText = (adminNotes != null && !adminNotes.trim().isBlank())
                    ? adminNotes.trim()
                    : "No specific reason provided by administrator.";
            actionCallout =
                "<div style='background-color: #fef2f2; border-left: 4px solid #ef4444; border-radius: 6px; padding: 14px 18px; margin: 18px 0;'>" +
                "  <strong style='color: #991b1b; font-size: 14px;'>Purchase Order Denied</strong>" +
                "  <p style='margin: 6px 0 0; color: #b91c1c; font-size: 13px; line-height: 1.5;'>" +
                "    This purchase order was not approved. Please review the administrator's feedback below and revise if necessary." +
                "  </p>" +
                "  <div style='margin-top: 8px; font-size: 13px; color: #7f1d1d; background: #fee2e2; padding: 8px 12px; border-radius: 4px;'>" +
                "    <strong>Reason / Notes:</strong> " + reasonText +
                "  </div>" +
                "</div>";
        }

        StringBuilder itemsHtml = new StringBuilder();
        if (o.getItems() != null) {
            for (OrderItemResponseDto item : o.getItems()) {
                String skuName = item.getSkuName() != null ? item.getSkuName() : (item.getGenericName() != null ? item.getGenericName() : "—");
                String brandName = item.getBrandName() != null ? item.getBrandName() : "—";
                String packaging = item.getPackagingUnit() != null ? item.getPackagingUnit() : "Units";
                long units = item.getOrderedUnits() != null ? item.getOrderedUnits() : 0L;
                long price = item.getPrice() != null ? item.getPrice() : 0L;
                double pricePerUnit = item.getPricePerUnit() != null ? item.getPricePerUnit() : (units > 0 ? (double) price / units : 0.0);

                itemsHtml.append(String.format(
                        "<tr>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb;'><strong>%s</strong></td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #4b5563;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #64748b;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: center; font-weight: 600;'>%,d</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: right;'>₱%,.2f</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>₱%,d</td>" +
                        "</tr>",
                        skuName, brandName, packaging, units, pricePerUnit, price
                ));
            }
        }

        String resolvedReviewer = reviewerName != null && !reviewerName.isBlank() ? reviewerName : "Administrator";
        String resolvedFacility = facilityName != null && !facilityName.isBlank() ? facilityName : "—";
        String pharmacyName = o.getPharmacyName() != null ? o.getPharmacyName() : "—";
        String supplierName = o.getSupplierName() != null && !o.getSupplierName().isBlank() ? o.getSupplierName() : "—";
        long totalCost = o.getTotalPrice() != null ? o.getTotalPrice() : 0L;
        long totalUnits = o.getTotalOrderedUnits() != null ? o.getTotalOrderedUnits() : 0L;
        String notesDisplay = (adminNotes != null && !adminNotes.trim().isBlank())
                ? adminNotes.trim()
                : (o.getNotes() != null && !o.getNotes().isBlank() ? o.getNotes().trim() : "None provided");
        String priority = o.getPriority() != null ? o.getPriority() : "Normal";
        int itemCount = o.getItems() != null ? o.getItems().size() : 0;

        String reviewUrl = String.format("%s/procurement/requested-orders", frontendUrl);

        return String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b;'>" +
                "  <div style='max-width: 640px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);'>" +
                "    <div style='background: %s; padding: 24px; color: #ffffff;'>" +
                "      <span style='font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; opacity: 0.9;'>Exakt Med Procurement Alert</span>" +
                "      <h1 style='margin: 6px 0 0; font-size: 22px; font-weight: 700;'>Purchase Order %s</h1>" +
                "    </div>" +
                "    <div style='padding: 24px;'>" +
                "      %s" +
                "      <div style='background-color: #f1f5f9; border-radius: 8px; padding: 14px 18px; margin: 18px 0; font-size: 13px; line-height: 1.6;'>" +
                "        <table style='width: 100%%; border-collapse: collapse;'>" +
                "          <tr><td style='color: #64748b; width: 35%%;'>Purchase Order #:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Status:</td><td><span style='background-color: %s; color: %s; font-weight: 700; padding: 3px 8px; border-radius: 4px; font-size: 11px;'>%s</span></td></tr>" +
                "          <tr><td style='color: #64748b;'>Facility:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Pharmacy:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Vendor / Supplier:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Reviewed By:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Priority:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Total Units:</td><td><strong>%d item(s) (%,d units)</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Total PO Amount:</td><td style='font-weight: 700; font-size: 15px; color: #0f172a;'>₱%,d</td></tr>" +
                "          <tr><td style='color: #64748b;'>Review Notes:</td><td style='color: #334155; font-style: italic;'>%s</td></tr>" +
                "        </table>" +
                "      </div>" +
                "      <h3 style='font-size: 14px; font-weight: 700; margin: 20px 0 8px; color: #1e293b;'>Order Line Items</h3>" +
                "      <table style='width: 100%%; border-collapse: collapse; font-size: 12px; margin-bottom: 24px;'>" +
                "        <thead>" +
                "          <tr style='background-color: #f8fafc; border-bottom: 2px solid #cbd5e1; text-align: left; color: #64748b; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em;'>" +
                "            <th style='padding: 8px 12px;'>SKU / Description</th>" +
                "            <th style='padding: 8px 12px;'>Brand</th>" +
                "            <th style='padding: 8px 12px;'>Packaging</th>" +
                "            <th style='padding: 8px 12px; text-align: center;'>Qty</th>" +
                "            <th style='padding: 8px 12px; text-align: right;'>Unit Price</th>" +
                "            <th style='padding: 8px 12px; text-align: right;'>Subtotal</th>" +
                "          </tr>" +
                "        </thead>" +
                "        <tbody>%s</tbody>" +
                "      </table>" +
                "      <div style='text-align: center; margin: 28px 0 12px;'>" +
                "        <a href='%s' style='display: inline-block; background-color: %s; color: #ffffff; text-decoration: none; padding: 12px 28px; font-size: 14px; font-weight: 600; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>View Requested Orders in Exakt Med</a>" +
                "      </div>" +
                "    </div>" +
                "    <div style='background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 14px 24px; font-size: 11px; color: #94a3b8; text-align: center;'>" +
                "      This is an automated operational alert generated by Exakt Med Inventory System. Please do not reply directly to this email." +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>",
                headerGradient,
                statusLabel,
                actionCallout,
                o.getPurchaseOrderNum(),
                statusBgColor,
                statusTextColor,
                statusLabel,
                resolvedFacility,
                pharmacyName,
                supplierName,
                resolvedReviewer,
                priority,
                itemCount,
                totalUnits,
                totalCost,
                notesDisplay,
                itemsHtml.toString(),
                reviewUrl,
                buttonColor
        );
    }

    /**
     * Sends an asynchronous HTML email notification to Pharmacist users assigned to the operating pharmacy
     * (and the quotation creator) when Procurement Approves (converts to PO) or Denies (rejects) a quotation.
     */
    @Async
    public void notifyPharmacistOfQuotationStatusUpdate(ReorderQuotationResponseDto quotation,
                                                       String facilityName,
                                                       String reviewerName) {
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.mail.enabled=false). Skipping quotation status email for: {}",
                    quotation != null ? quotation.getQuotationNumber() : "null");
            return;
        }

        if (quotation == null || quotation.getPharmacyId() == null) {
            log.warn("Cannot send quotation status email: quotation or pharmacyId is null.");
            return;
        }

        try {
            Long pharmacyId = quotation.getPharmacyId();

            // Collect recipient emails: assigned Pharmacists + quotation creator
            java.util.Set<String> recipientEmails = new java.util.LinkedHashSet<>(
                    userPharmacyLinkRepository.findPharmacistEmailsByPharmacyId(pharmacyId));

            if (quotation.getUserId() != null) {
                userRepository.findById(quotation.getUserId())
                        .filter(u -> u.getEmail() != null && !u.getEmail().trim().isBlank())
                        .ifPresent(u -> recipientEmails.add(u.getEmail().trim()));
            }

            if (recipientEmails.isEmpty()) {
                log.warn("No assigned Pharmacists with an email address found for pharmacy ID {} ('{}').",
                        pharmacyId, quotation.getPharmacyName());
                return;
            }

            boolean isApproved = (quotation.getStatus() == ReorderQuotation.Status.Approved);
            String statusLabel = isApproved ? "APPROVED" : "DENIED";

            // Build email subject and HTML message
            String subject = String.format("[Exakt Med] Reorder Quotation %s: %s (%s)",
                    statusLabel, quotation.getQuotationNumber(), quotation.getPharmacyName());

            String htmlBody = buildQuotationStatusUpdateHtmlTemplate(quotation, facilityName, reviewerName);

            // Dispatch MimeMessage via SMTP
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String senderAddress = (mailFrom != null && !mailFrom.trim().isBlank()) ? mailFrom.trim() : "noreply@exaktmed.com";
            helper.setFrom(senderAddress, "Exakt Med Inventory System");
            helper.setTo(recipientEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched quotation status update ({}) email for {} to {} recipient(s): {}",
                    statusLabel, quotation.getQuotationNumber(), recipientEmails.size(), recipientEmails);

        } catch (Exception ex) {
            log.error("Failed to send quotation status update email for quotation {}: {}",
                    quotation.getQuotationNumber(), ex.getMessage(), ex);
        }
    }

    private String buildQuotationStatusUpdateHtmlTemplate(ReorderQuotationResponseDto q,
                                                          String facilityName,
                                                          String reviewerName) {
        boolean isApproved = (q.getStatus() == ReorderQuotation.Status.Approved);
        String headerGradient = isApproved
                ? "linear-gradient(135deg, #059669, #047857)"   // Emerald Green
                : "linear-gradient(135deg, #dc2626, #b91c1c)";  // Crimson Red
        String statusLabel = isApproved ? "APPROVED" : "DENIED";
        String statusBgColor = isApproved ? "#dcfce7" : "#fee2e2";
        String statusTextColor = isApproved ? "#15803d" : "#b91c1c";
        String buttonColor = isApproved ? "#059669" : "#475569";

        String resolvedReviewer = reviewerName != null && !reviewerName.isBlank() ? reviewerName : "Procurement Officer";
        String resolvedFacility = facilityName != null && !facilityName.isBlank() ? facilityName : "—";
        String pharmacyName = q.getPharmacyName() != null ? q.getPharmacyName() : "—";
        String supplierName = q.getSupplierName() != null && !q.getSupplierName().isBlank() ? q.getSupplierName() : "—";
        String creatorName = q.getUserName() != null && !q.getUserName().isBlank() ? q.getUserName() : "Pharmacist";
        String priority = q.getPriority() != null ? q.getPriority() : "Normal";
        String poNumber = q.getPurchaseOrderNum() != null && !q.getPurchaseOrderNum().isBlank() ? q.getPurchaseOrderNum() : "—";

        String actionCallout;
        if (isApproved) {
            actionCallout = String.format(
                "<div style='background-color: #ecfdf5; border-left: 4px solid #10b981; border-radius: 6px; padding: 14px 18px; margin: 18px 0;'>" +
                "  <strong style='color: #065f46; font-size: 14px;'>Quotation Approved & Purchase Order Created</strong>" +
                "  <p style='margin: 6px 0 0; color: #047857; font-size: 13px; line-height: 1.5;'>" +
                "    Your reorder quotation <strong>%s</strong> has been reviewed and approved by %s. " +
                "    A formal Purchase Order (<strong>%s</strong>) has been generated for vendor ordering." +
                "  </p>" +
                "</div>",
                q.getQuotationNumber(), resolvedReviewer, poNumber
            );
        } else {
            String reasonText = (q.getRejectionReason() != null && !q.getRejectionReason().trim().isBlank())
                    ? q.getRejectionReason().trim()
                    : "No specific rejection reason provided by procurement.";
            actionCallout = String.format(
                "<div style='background-color: #fef2f2; border-left: 4px solid #ef4444; border-radius: 6px; padding: 14px 18px; margin: 18px 0;'>" +
                "  <strong style='color: #991b1b; font-size: 14px;'>Quotation Denied by Procurement</strong>" +
                "  <p style='margin: 6px 0 0; color: #b91c1c; font-size: 13px; line-height: 1.5;'>" +
                "    Your reorder quotation <strong>%s</strong> was not approved by %s." +
                "  </p>" +
                "  <div style='margin-top: 8px; font-size: 13px; color: #7f1d1d; background: #fee2e2; padding: 8px 12px; border-radius: 4px;'>" +
                "    <strong>Reason / Notes:</strong> %s" +
                "  </div>" +
                "</div>",
                q.getQuotationNumber(), resolvedReviewer, reasonText
            );
        }

        StringBuilder itemsHtml = new StringBuilder();
        long totalRequestedUnits = 0L;
        if (q.getItems() != null) {
            for (ReorderQuotationItemResponseDto item : q.getItems()) {
                String skuName = item.getSkuCode() != null ? item.getSkuCode() : (item.getGenericName() != null ? item.getGenericName() : "—");
                String brandName = item.getBrandName() != null ? item.getBrandName() : "—";
                String packaging = item.getPackagingUnit() != null ? item.getPackagingUnit() : "Units";
                long units = item.getRequestedUnits() != null ? item.getRequestedUnits() : 0L;
                totalRequestedUnits += units;

                itemsHtml.append(String.format(
                        "<tr>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb;'><strong>%s</strong></td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #4b5563;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; color: #64748b;'>%s</td>" +
                        "<td style='padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: center; font-weight: 600;'>%,d</td>" +
                        "</tr>",
                        skuName, brandName, packaging, units
                ));
            }
        }

        int itemCount = q.getItems() != null ? q.getItems().size() : 0;
        String notesDisplay = (q.getRejectionReason() != null && !q.getRejectionReason().isBlank())
                ? q.getRejectionReason().trim()
                : (q.getNotes() != null && !q.getNotes().isBlank() ? q.getNotes().trim() : "None provided");

        String reviewUrl = String.format("%s/pharmacist/reorder-quotations", frontendUrl);

        return String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b;'>" +
                "  <div style='max-width: 620px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);'>" +
                "    <div style='background: %s; padding: 24px; color: #ffffff;'>" +
                "      <span style='font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; opacity: 0.9;'>Exakt Med Pharmacist Notification</span>" +
                "      <h1 style='margin: 6px 0 0; font-size: 22px; font-weight: 700;'>Reorder Quotation %s</h1>" +
                "    </div>" +
                "    <div style='padding: 24px;'>" +
                "      %s" +
                "      <div style='background-color: #f1f5f9; border-radius: 8px; padding: 14px 18px; margin: 18px 0; font-size: 13px; line-height: 1.6;'>" +
                "        <table style='width: 100%%; border-collapse: collapse;'>" +
                "          <tr><td style='color: #64748b; width: 35%%;'>Quotation #:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Status:</td><td><span style='background-color: %s; color: %s; font-weight: 700; padding: 3px 8px; border-radius: 4px; font-size: 11px;'>%s</span></td></tr>" +
                "          <tr><td style='color: #64748b;'>Purchase Order #:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Facility:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Pharmacy:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Vendor / Supplier:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Reviewed By:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Requested By:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Priority:</td><td><strong>%s</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Total Requested Units:</td><td><strong>%d item(s) (%,d units)</strong></td></tr>" +
                "          <tr><td style='color: #64748b;'>Review Notes:</td><td style='color: #334155; font-style: italic;'>%s</td></tr>" +
                "        </table>" +
                "      </div>" +
                "      <h3 style='font-size: 14px; font-weight: 700; margin: 20px 0 8px; color: #1e293b;'>Quotation Line Items</h3>" +
                "      <table style='width: 100%%; border-collapse: collapse; font-size: 12px; margin-bottom: 24px;'>" +
                "        <thead>" +
                "          <tr style='background-color: #f8fafc; border-bottom: 2px solid #cbd5e1; text-align: left; color: #64748b; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em;'>" +
                "            <th style='padding: 8px 12px;'>SKU / Description</th>" +
                "            <th style='padding: 8px 12px;'>Brand</th>" +
                "            <th style='padding: 8px 12px;'>Packaging</th>" +
                "            <th style='padding: 8px 12px; text-align: center;'>Requested Qty</th>" +
                "          </tr>" +
                "        </thead>" +
                "        <tbody>%s</tbody>" +
                "      </table>" +
                "      <div style='text-align: center; margin: 28px 0 12px;'>" +
                "        <a href='%s' style='display: inline-block; background-color: %s; color: #ffffff; text-decoration: none; padding: 12px 28px; font-size: 14px; font-weight: 600; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>View Reorder Quotations in Exakt Med</a>" +
                "      </div>" +
                "    </div>" +
                "    <div style='background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 14px 24px; font-size: 11px; color: #94a3b8; text-align: center;'>" +
                "      This is an automated operational alert generated by Exakt Med Inventory System. Please do not reply directly to this email." +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>",
                headerGradient,
                statusLabel,
                actionCallout,
                q.getQuotationNumber(),
                statusBgColor,
                statusTextColor,
                statusLabel,
                poNumber,
                resolvedFacility,
                pharmacyName,
                supplierName,
                resolvedReviewer,
                creatorName,
                priority,
                itemCount,
                totalRequestedUnits,
                notesDisplay,
                itemsHtml.toString(),
                reviewUrl,
                buttonColor
        );
    }
}
