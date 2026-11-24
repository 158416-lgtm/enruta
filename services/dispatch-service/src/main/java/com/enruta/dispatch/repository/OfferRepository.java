package com.enruta.dispatch.repository;

import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.domain.OfferStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByStatusAndExpiresAtBefore(OfferStatus status, Instant now);

    List<Offer> findByDriverIdAndStatus(UUID driverId, OfferStatus status);

    @Query("select o.driverId from Offer o where o.shipmentId = :shipmentId")
    List<UUID> findDriverIdsByShipmentId(@Param("shipmentId") UUID shipmentId);

    List<Offer> findByShipmentIdAndStatus(UUID shipmentId, OfferStatus status);

    long countByShipmentIdAndStatus(UUID shipmentId, OfferStatus status);

    @Query("select count(o) from Offer o where o.shipmentId = :shipmentId and o.round = :round")
    long countByShipmentAndRound(@Param("shipmentId") UUID shipmentId, @Param("round") short round);

    /**
     * Segunda barrera: solo una transaccion puede pasar de CREATED a ACCEPTED. Devuelve 0 si otra
     * ya lo hizo (o si la oferta expiro).
     */
    /** Al ganar un conductor, las demas ofertas del envio dejan de estar disponibles. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update Offer o set o.status = :next "
                    + "where o.shipmentId = :shipmentId and o.status = :current")
    int updateStatusForShipment(
            @Param("shipmentId") UUID shipmentId,
            @Param("current") OfferStatus current,
            @Param("next") OfferStatus next);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Offer o set o.status = :next where o.id = :id and o.status = :current")
    int updateStatusIfCurrent(
            @Param("id") UUID id,
            @Param("current") OfferStatus current,
            @Param("next") OfferStatus next);
}
