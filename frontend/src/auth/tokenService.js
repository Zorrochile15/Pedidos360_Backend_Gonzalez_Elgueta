import { fetchAuthSession } from "aws-amplify/auth";

export async function getAccessToken() {
  try {
    const session = await fetchAuthSession();

    const accessToken = session.tokens?.accessToken;

    if (!accessToken) {
      return null;
    }

    return accessToken.toString();
  } catch (error) {
    console.error(
      "No fue posible obtener el token de acceso:",
      error
    );

    return null;
  }
}