import { expect, test } from "@playwright/test";

test("renders the correct role workspace with accessible navigation", async ({ page }, testInfo) => {
  await page.goto("/");
  const expectedRole = testInfo.project.name.startsWith("customer")
    ? "Customer"
    : testInfo.project.name.startsWith("provider") ? "Provider" : "Admin";

  await expect(page.getByRole("navigation", { name: `${expectedRole} navigation` })).toBeVisible();
  await expect(page.getByRole("main")).toBeVisible();
  await expect(page.locator("body")).not.toContainText("undefined");
});

test("supports keyboard access to the content skip link", async ({ page }) => {
  await page.goto("/");
  await page.keyboard.press("Tab");
  const skipLink = page.getByRole("link", { name: "Skip to content" });
  await expect(skipLink).toBeFocused();
  await skipLink.press("Enter");
  await expect(page).toHaveURL(/#main-content$/);
});
