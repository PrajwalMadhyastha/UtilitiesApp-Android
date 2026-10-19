package com.prajwal.utilities.tools.challantracker

import com.prajwal.utilities.tools.challantracker.data.network.ChallanApiItem
import com.prajwal.utilities.tools.challantracker.data.network.ChallanApiResponse
import com.prajwal.utilities.tools.challantracker.data.network.MockChallanDataProvider
import com.prajwal.utilities.tools.challantracker.model.Challan
import com.prajwal.utilities.tools.challantracker.model.ChallanStatus
import com.prajwal.utilities.tools.challantracker.model.Vehicle
import com.prajwal.utilities.tools.challantracker.model.VehicleType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallanTrackerTest {

    @Test
    fun testPlateFormatting() {
        val v1 = Vehicle("KA01MJ2024", "Ather", VehicleType.TWO_WHEELER)
        assertEquals("KA-01-MJ-2024", v1.formattedPlate)

        val v2 = Vehicle("ka04na8812", "City", VehicleType.FOUR_WHEELER)
        assertEquals("KA-04-NA-8812", v2.formattedPlate)

        val v3 = Vehicle("MH-12-DE-1234", "Bike", VehicleType.TWO_WHEELER)
        assertEquals("MH-12-DE-1234", v3.formattedPlate)

        val v4 = Vehicle("DL1C1234", "Car", VehicleType.FOUR_WHEELER)
        assertEquals("DL-1-C-1234", v4.formattedPlate)
    }

    @Test
    fun testMockDataProviderDefaults() {
        val defaults = MockChallanDataProvider.defaultSampleVehicles
        assertEquals(3, defaults.size)

        // Ather vehicle should have 1 pending violation with photo evidence
        val atherChallans = MockChallanDataProvider.getMockChallansForVehicle("KA01MJ2024")
        assertEquals(1, atherChallans.size)
        assertEquals(ChallanStatus.PENDING, atherChallans.first().status)
        assertNotNull(atherChallans.first().imageUrl)
        assertTrue(atherChallans.first().imageUrl!!.startsWith("https://"))
        assertEquals(500.0, atherChallans.first().fineAmount, 0.01)

        // City vehicle should have 2 violations (1 pending, 1 paid)
        val cityChallans = MockChallanDataProvider.getMockChallansForVehicle("KA04NA8812")
        assertEquals(2, cityChallans.size)
        assertTrue(cityChallans.any { it.status == ChallanStatus.PENDING })
        assertTrue(cityChallans.any { it.status == ChallanStatus.PAID })

        // Activa vehicle should have 0 violations (clean record)
        val activaChallans = MockChallanDataProvider.getMockChallansForVehicle("KA05XY4421")
        assertTrue(activaChallans.isEmpty())
    }

    @Test
    fun testApiJsonParsingWithMoshi() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(ChallanApiResponse::class.java)

        val json = """
            {
              "status": 200,
              "message": "success",
              "data": {
                "rc_number": "KA01AB1234",
                "challans": [
                  {
                    "challan_number": "CH101",
                    "offense": "NO HELMET",
                    "amount": 500,
                    "date": "2026-10-10",
                    "location": "MG Road",
                    "status": "Pending",
                    "proof_url": "https://example.com/photo.jpg"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = adapter.fromJson(json)
        assertNotNull(response)
        val item = response?.data?.challans?.firstOrNull()
        assertNotNull(item)
        assertEquals("CH101", item?.resolvedNumber)
        assertEquals("NO HELMET", item?.resolvedOffense)
        assertEquals(500.0, item?.resolvedAmount ?: 0.0, 0.01)
        assertEquals("https://example.com/photo.jpg", item?.resolvedImageUrl)
        assertEquals("Pending", item?.resolvedStatus)
    }

    @Test
    fun testDirectChallanJsonFormat() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(ChallanApiResponse::class.java)

        val json = """
            {
              "challans": [
                {
                  "challan_no": "BTP8899",
                  "violation_details": "SPEEDING",
                  "fine_amount": 1000,
                  "challan_date": "2026-09-01",
                  "place": "Hebbal",
                  "payment_status": "PAID"
                }
              ]
            }
        """.trimIndent()

        val response = adapter.fromJson(json)
        val item = response?.directChallans?.firstOrNull()
        assertNotNull(item)
        assertEquals("BTP8899", item?.resolvedNumber)
        assertEquals("SPEEDING", item?.resolvedOffense)
        assertEquals(1000.0, item?.resolvedAmount ?: 0.0, 0.01)
        assertEquals("PAID", item?.resolvedStatus)
        assertEquals("Hebbal", item?.resolvedLocation)
    }

    @Test
    fun testPendingFineCalculation() {
        val challans = listOf(
            Challan(
                challanNumber = "C1",
                vehicleNumber = "KA01AB1234",
                offenseDescription = "Helmet",
                fineAmount = 500.0,
                violationDate = "2026-10-01",
                location = "Bangalore",
                status = ChallanStatus.PENDING
            ),
            Challan(
                challanNumber = "C2",
                vehicleNumber = "KA01AB1234",
                offenseDescription = "Signal Jump",
                fineAmount = 1000.0,
                violationDate = "2026-10-02",
                location = "Bangalore",
                status = ChallanStatus.PENDING
            ),
            Challan(
                challanNumber = "C3",
                vehicleNumber = "KA01AB1234",
                offenseDescription = "Parking",
                fineAmount = 500.0,
                violationDate = "2026-09-15",
                location = "Bangalore",
                status = ChallanStatus.PAID
            )
        )

        val pending = challans.filter { it.status == ChallanStatus.PENDING }
        val totalFine = pending.sumOf { it.fineAmount }

        assertEquals(2, pending.size)
        assertEquals(1500.0, totalFine, 0.01)
    }

    @Test
    fun testResilientJsonParsing() {
        val rawJson = """
            {
              "status": "success",
              "result": [
                {
                  "challan_number": "KA012024999",
                  "violation": "RIDING WITHOUT HELMET",
                  "fine_amount": "500",
                  "date": "14-10-2026",
                  "place": "MG Road Trinity Circle",
                  "status": "Pending",
                  "image_url": "https://example.com/btp_camera.jpg"
                }
              ]
            }
        """.trimIndent()

        val parsed = com.prajwal.utilities.tools.challantracker.data.network.ChallanJsonParser.parse("KA01MJ2024", rawJson)
        assertEquals(1, parsed.size)
        assertEquals("KA012024999", parsed.first().challanNumber)
        assertEquals("RIDING WITHOUT HELMET", parsed.first().offenseDescription)
        assertEquals(500.0, parsed.first().fineAmount, 0.01)
        assertEquals("https://example.com/btp_camera.jpg", parsed.first().imageUrl)
        assertEquals(ChallanStatus.PENDING, parsed.first().status)
    }

    @Test
    fun testKarnatakaOneScrapedTableParsing() {
        val scrapedJson = """
            [
              {
                "challan_number": "TRF-BN-2024-8841",
                "offense": "Defective / Improper Number Plate",
                "fine_amount": 500,
                "violation_date": "14-Oct-2026 11:32 AM",
                "location": "MG Road Junction, Bengaluru",
                "image_url": "https://www.karnatakaone.gov.in/PoliceCollectionOfFine/GetImage?notice=8841",
                "status": "PENDING"
              },
              {
                "challan_number": "TRF-BN-2024-9102",
                "offense": "Pillion Rider Not Wearing Protective Headgear (Helmet)",
                "fine_amount": 500,
                "violation_date": "02-Sep-2026 04:15 PM",
                "location": "Indiranagar 100ft Road, Bengaluru",
                "image_url": "https://www.karnatakaone.gov.in/PoliceCollectionOfFine/GetImage?notice=9102",
                "status": "PENDING"
              }
            ]
        """.trimIndent()

        val parsed = com.prajwal.utilities.tools.challantracker.data.network.ChallanJsonParser.parse("KA01AB1234", scrapedJson)
        assertEquals(2, parsed.size)

        val first = parsed[0]
        assertEquals("TRF-BN-2024-8841", first.challanNumber)
        assertEquals("KA01AB1234", first.vehicleNumber)
        assertEquals("Defective / Improper Number Plate", first.offenseDescription)
        assertEquals(500.0, first.fineAmount, 0.01)
        assertEquals("https://www.karnatakaone.gov.in/PoliceCollectionOfFine/GetImage?notice=8841", first.imageUrl)
        assertEquals(ChallanStatus.PENDING, first.status)

        val second = parsed[1]
        assertEquals("TRF-BN-2024-9102", second.challanNumber)
        assertEquals(500.0, second.fineAmount, 0.01)
        assertEquals("https://www.karnatakaone.gov.in/PoliceCollectionOfFine/GetImage?notice=9102", second.imageUrl)
    }

    @Test
    fun testKarnatakaOneScriptGeneration() {
        val fillScript = com.prajwal.utilities.tools.challantracker.karnatakaone.KarnatakaOneScripts.getFillVehicleScript("KA01AB1234")
        assertTrue(fillScript.contains("k1FillVehicle('KA01AB1234')"))

        val mobileScript = com.prajwal.utilities.tools.challantracker.karnatakaone.KarnatakaOneScripts.getFillMobileScript("9876543210")
        assertTrue(mobileScript.contains("k1FillMobile('9876543210')"))

        val scrapeScript = com.prajwal.utilities.tools.challantracker.karnatakaone.KarnatakaOneScripts.getScrapeViolationsScript("KA01AB1234")
        assertTrue(scrapeScript.contains("k1ScrapeViolations('KA01AB1234')"))
    }
}
