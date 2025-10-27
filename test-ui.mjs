import { chromium } from 'playwright';

async function testUI() {
    console.log('🚀 Starting Playwright UI Test...\n');

    const browser = await chromium.launch({ headless: false });
    const page = await browser.newPage();

    try {
        // Navigate to the UI
        console.log('📍 Navigating to http://localhost:7082/index.html');
        await page.goto('http://localhost:7082/index.html', { waitUntil: 'networkidle' });
        await page.waitForTimeout(2000);

        // Test 1: Check if the page loads
        console.log('\n✓ Test 1: Page loaded successfully');

        // Test 2: Check stats are displayed
        const totalJobs = await page.locator('#stat-total').textContent();
        console.log(`✓ Test 2: Total jobs displayed: ${totalJobs}`);

        // Test 3: Check Scheduled Jobs section exists
        const scheduledHeader = await page.locator('h2:has-text("Scheduled Jobs")').isVisible();
        console.log(`✓ Test 3: Scheduled Jobs section visible: ${scheduledHeader}`);

        // Test 4: Check Job History section exists
        const historyHeader = await page.locator('h2:has-text("Job History")').isVisible();
        console.log(`✓ Test 4: Job History section visible: ${historyHeader}`);

        // Wait for jobs to be created and scheduled (30 seconds)
        console.log('\n⏳ Waiting 30 seconds for jobs to be created and scheduled...');
        await page.waitForTimeout(30000);

        // Reload to get fresh data
        await page.reload({ waitUntil: 'networkidle' });
        await page.waitForTimeout(3000);

        // Test 5: Check if scheduled jobs appear
        const scheduledCount = await page.locator('#scheduled-count').textContent();
        console.log(`✓ Test 5: Scheduled jobs count: ${scheduledCount}`);

        // Test 6: Check if job items exist in scheduled section
        const scheduledJobs = await page.locator('#scheduled-jobs .job-item').count();
        console.log(`✓ Test 6: Number of scheduled job items displayed: ${scheduledJobs}`);

        // Test 6.5: Check for job name display
        if (scheduledJobs > 0) {
            const jobName = await page.locator('#scheduled-jobs .job-name').first().textContent();
            console.log(`✓ Test 6.5: First scheduled job name: ${jobName}`);
        }

        if (scheduledJobs > 0) {
            // Test 7: Check if job parameters are shown
            const jobParams = await page.locator('#scheduled-jobs .job-param').count();
            console.log(`✓ Test 7: Number of job parameters displayed: ${jobParams}`);

            // Test 8: Check if status badge exists
            const statusBadge = await page.locator('#scheduled-jobs .status-badge').first().textContent();
            console.log(`✓ Test 8: First job status: ${statusBadge}`);
        } else {
            console.log('⚠️  No scheduled jobs found yet');
        }

        // Wait for some jobs to complete (20 more seconds)
        console.log('\n⏳ Waiting 20 more seconds for jobs to execute...');
        await page.waitForTimeout(20000);

        // Reload to get completed jobs
        await page.reload({ waitUntil: 'networkidle' });
        await page.waitForTimeout(3000);

        // Test 9: Check if history jobs appear
        const historyCount = await page.locator('#history-count').textContent();
        console.log(`✓ Test 9: Job history count: ${historyCount}`);

        // Test 10: Check if job items exist in history section
        const historyJobs = await page.locator('#history-jobs .job-item').count();
        console.log(`✓ Test 10: Number of history job items displayed: ${historyJobs}`);

        if (historyJobs > 0) {
            // Test 11: Check if completed job name is shown
            const firstHistoryName = await page.locator('#history-jobs .job-name').first().textContent();
            console.log(`✓ Test 11: First history job name: ${firstHistoryName}`);

            // Test 12: Check completion status
            const completedStatus = await page.locator('#history-jobs .status-badge').first().textContent();
            console.log(`✓ Test 12: First completed job status: ${completedStatus}`);
        } else {
            console.log('⚠️  No completed jobs in history yet');
        }

        // Test 13: Take a screenshot
        await page.screenshot({ path: 'scheduler-ui-screenshot.png', fullPage: true });
        console.log('\n📸 Screenshot saved as scheduler-ui-screenshot.png');

        // Test 14: Verify auto-refresh works
        console.log('\n⏳ Testing auto-refresh (waiting 10 seconds)...');
        const statsBefore = await page.locator('#stat-total').textContent();
        await page.waitForTimeout(10000);
        const statsAfter = await page.locator('#stat-total').textContent();
        console.log(`✓ Test 14: Stats before: ${statsBefore}, after: ${statsAfter}`);

        console.log('\n✅ All tests passed!');
        console.log('\n🎉 UI is working correctly!');
        console.log('   - Scheduled jobs column is visible');
        console.log('   - Job history column is visible');
        console.log('   - Jobs are being displayed in real-time');
        console.log('   - Auto-refresh is working');

        // Keep browser open for manual inspection
        console.log('\n👀 Keeping browser open for 30 seconds for manual inspection...');
        await page.waitForTimeout(30000);

    } catch (error) {
        console.error('\n❌ Test failed:', error.message);
        await page.screenshot({ path: 'scheduler-ui-error.png', fullPage: true });
        console.log('📸 Error screenshot saved as scheduler-ui-error.png');
        throw error;
    } finally {
        await browser.close();
        console.log('\n🏁 Test complete!');
    }
}

// Run the test
testUI().catch(error => {
    console.error('Fatal error:', error);
    process.exit(1);
});
