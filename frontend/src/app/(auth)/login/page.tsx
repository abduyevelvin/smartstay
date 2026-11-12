"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useRouter } from "next/navigation";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useLogin } from "@/lib/hooks/useAuth";
import { getErrorMessage } from "@/lib/error";

const schema = z.object({
    email: z.string().email("Invalid email address"),
    password: z.string().min(1, "Password is required"),
});

type FormData = z.infer<typeof schema>;

export default function LoginPage() {
    const router = useRouter();
    const {mutate: login, isPending, error} = useLogin();

    const form = useForm<FormData>({
        resolver: zodResolver(schema),
        defaultValues: {email: "", password: ""},
    });

    const onSubmit = (data: FormData) => {
        login(data, {onSuccess: () => router.push("/")});
    };

    return (
        <Card className="w-full max-w-md">
            <CardHeader>
                <CardTitle>Welcome back</CardTitle>
                <CardDescription>Sign in to your SmartStay account</CardDescription>
            </CardHeader>
            <CardContent>
                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                        <FormField
                            control={form.control}
                            name="email"
                            render={({field}) => (
                                <FormItem>
                                    <FormLabel>Email</FormLabel>
                                    <FormControl>
                                        <Input placeholder="you@example.com" type="email" {...field} />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name="password"
                            render={({field}) => (
                                <FormItem>
                                    <FormLabel>Password</FormLabel>
                                    <FormControl>
                                        <Input placeholder="••••••••" type="password" {...field} />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />

                        {error && (
                            <p className="text-sm text-destructive">{getErrorMessage(error)}</p>
                        )}

                        <Button type="submit" className="w-full" disabled={isPending}>
                            {isPending ? "Signing in…" : "Sign in"}
                        </Button>
                    </form>
                </Form>

                <p className="mt-4 text-center text-sm text-muted-foreground">
                    Don&apos;t have an account?{" "}
                    <Link href="/register" className="text-primary hover:underline">
                        Register
                    </Link>
                </p>
            </CardContent>
        </Card>
    );
}
